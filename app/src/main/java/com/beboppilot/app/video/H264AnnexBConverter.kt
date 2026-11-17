package com.beboppilot.app.video

import java.nio.ByteBuffer

data class H264Dimensions(val width: Int, val height: Int)

data class AnnexBSampleResult(
    val buffer: ByteBuffer,
    val hasIdrSlice: Boolean,
    val hasSei: Boolean,
)

/**
 * Utilidades para NALUs H.264 Annex B (MediaCodec / MediaMuxer) y lectura básica de resolución en SPS.
 */
object H264AnnexBConverter {

    private val ANNEX_B_START_CODE = byteArrayOf(0x00, 0x00, 0x00, 0x01)

    fun ensureAnnexBPrefix(nalu: ByteArray): ByteArray {
        if (nalu.size >= 4 &&
            nalu[0] == 0.toByte() &&
            nalu[1] == 0.toByte() &&
            nalu[2] == 0.toByte() &&
            nalu[3] == 1.toByte()
        ) {
            return nalu.copyOf()
        }
        if (nalu.size >= 3 &&
            nalu[0] == 0.toByte() &&
            nalu[1] == 0.toByte() &&
            nalu[2] == 1.toByte()
        ) {
            val out = ByteArray(nalu.size + 1)
            System.arraycopy(ANNEX_B_START_CODE, 0, out, 0, 4)
            System.arraycopy(nalu, 3, out, 4, nalu.size - 3)
            return out
        }
        val out = ByteArray(nalu.size + 4)
        System.arraycopy(ANNEX_B_START_CODE, 0, out, 0, 4)
        System.arraycopy(nalu, 0, out, 4, nalu.size)
        return out
    }

    /**
     * Normaliza un Access Unit a formato Annex B estricto con códigos de inicio de 4 bytes
     * (`00 00 00 01`), que es el formato esperado por Android MediaMuxer/MPEG4Writer para video/avc.
     * Descarta SPS (7), PPS (8), AUD (9) y Filler (12) ya entregados en csd-0/csd-1.
     */
    fun sanitizeAnnexBForMediaMuxer(annexB: ByteArray, length: Int): AnnexBSampleResult? {
        if (length <= 4) return null

        val nalRanges = splitAnnexBNals(annexB, length)
        if (nalRanges.isEmpty()) return null

        var totalBytes = 0
        var hasVideoSlice = false
        var hasIdrSlice = false
        var hasSei = false
        val filtered = ArrayList<IntRange>(nalRanges.size)

        for (range in nalRanges) {
            val naluLen = range.last - range.first + 1
            if (naluLen <= 0) continue
            val naluType = annexB[range.first].toInt() and 0x1F
            if (naluType == 7 || naluType == 8 || naluType == 9 || naluType == 12) {
                continue
            }
            if (naluType == 6) {
                hasSei = true
            }
            if (naluType in 1..5) {
                hasVideoSlice = true
            }
            if (naluType == 5) {
                hasIdrSlice = true
            }
            filtered.add(range)
            totalBytes += ANNEX_B_START_CODE.size + naluLen
        }

        if (filtered.isEmpty() || !hasVideoSlice) return null

        val out = ByteBuffer.allocateDirect(totalBytes)
        for (range in filtered) {
            val naluLen = range.last - range.first + 1
            out.put(ANNEX_B_START_CODE)
            out.put(annexB, range.first, naluLen)
        }
        out.flip()
        return AnnexBSampleResult(
            buffer = out,
            hasIdrSlice = hasIdrSlice,
            hasSei = hasSei,
        )
    }

    private fun splitAnnexBNals(data: ByteArray, limit: Int): List<IntRange> {
        val starts = ArrayList<Pair<Int, Int>>(4) // (startCodeOffset, payloadOffset)
        var i = 0
        while (i <= limit - 3) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (i <= limit - 4 && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    starts.add(i to (i + 4))
                    i += 4
                    continue
                } else if (data[i + 2] == 1.toByte()) {
                    starts.add(i to (i + 3))
                    i += 3
                    continue
                }
            }
            i++
        }

        if (starts.isEmpty()) {
            return listOf(0 until limit)
        }

        val result = ArrayList<IntRange>(starts.size)
        for (idx in starts.indices) {
            val payloadStart = starts[idx].second
            val payloadEndExclusive = if (idx + 1 < starts.size) starts[idx + 1].first else limit
            if (payloadEndExclusive > payloadStart) {
                result.add(payloadStart until payloadEndExclusive)
            }
        }
        return result
    }

    fun parseSpsDimensions(spsAnnexB: ByteArray): H264Dimensions? {
        return runCatching {
            val payloadOffset = when {
                spsAnnexB.size > 4 && spsAnnexB[0] == 0.toByte() && spsAnnexB[1] == 0.toByte() &&
                    spsAnnexB[2] == 0.toByte() && spsAnnexB[3] == 1.toByte() -> 4
                spsAnnexB.size > 3 && spsAnnexB[0] == 0.toByte() && spsAnnexB[1] == 0.toByte() &&
                    spsAnnexB[2] == 1.toByte() -> 3
                else -> 0
            }
            if (payloadOffset >= spsAnnexB.size) return null
            val nalType = spsAnnexB[payloadOffset].toInt() and 0x1F
            if (nalType != 7) return null

            val rbsp = removeEmulationPreventionBytes(spsAnnexB, payloadOffset + 1, spsAnnexB.size)
            val br = BitReader(rbsp)
            val profileIdc = br.readBits(8)
            br.readBits(8) // constraint flags + reserved
            br.readBits(8) // level_idc
            br.readUE() // seq_parameter_set_id

            var chromaFormatIdc = 1
            if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 ||
                profileIdc == 244 || profileIdc == 44 || profileIdc == 83 ||
                profileIdc == 86 || profileIdc == 118 || profileIdc == 128 ||
                profileIdc == 138 || profileIdc == 139 || profileIdc == 134
            ) {
                chromaFormatIdc = br.readUE()
                if (chromaFormatIdc == 3) {
                    br.readBit() // separate_colour_plane_flag
                }
                br.readUE() // bit_depth_luma_minus8
                br.readUE() // bit_depth_chroma_minus8
                br.readBit() // qpprime_y_zero_transform_bypass_flag
                val seqScalingMatrixPresent = br.readBit()
                if (seqScalingMatrixPresent == 1) {
                    val count = if (chromaFormatIdc != 3) 8 else 12
                    for (idx in 0 until count) {
                        if (br.readBit() == 1) {
                            skipScalingList(br, if (idx < 6) 16 else 64)
                        }
                    }
                }
            }

            br.readUE() // log2_max_frame_num_minus4
            val picOrderCntType = br.readUE()
            if (picOrderCntType == 0) {
                br.readUE()
            } else if (picOrderCntType == 1) {
                br.readBit()
                br.readSE()
                br.readSE()
                val numRefFramesInPicOrderCntCycle = br.readUE()
                for (j in 0 until numRefFramesInPicOrderCntCycle) {
                    br.readSE()
                }
            }
            br.readUE() // max_num_ref_frames
            br.readBit() // gaps_in_frame_num_value_allowed_flag

            val picWidthInMbsMinus1 = br.readUE()
            val picHeightInMapUnitsMinus1 = br.readUE()
            val frameMbsOnlyFlag = br.readBit()
            if (frameMbsOnlyFlag == 0) {
                br.readBit() // mb_adaptive_frame_field_flag
            }
            br.readBit() // direct_8x8_inference_flag

            var cropLeft = 0
            var cropRight = 0
            var cropTop = 0
            var cropBottom = 0
            if (br.readBit() == 1) {
                cropLeft = br.readUE()
                cropRight = br.readUE()
                cropTop = br.readUE()
                cropBottom = br.readUE()
            }

            val subWidthC = if (chromaFormatIdc == 1 || chromaFormatIdc == 2) 2 else 1
            val subHeightC = if (chromaFormatIdc == 1) 2 else 1
            val cropUnitX = if (chromaFormatIdc == 0) 1 else subWidthC
            val cropUnitY = (if (chromaFormatIdc == 0) 1 else subHeightC) * (2 - frameMbsOnlyFlag)

            val width = ((picWidthInMbsMinus1 + 1) * 16) - (cropLeft + cropRight) * cropUnitX
            val height = ((2 - frameMbsOnlyFlag) * (picHeightInMapUnitsMinus1 + 1) * 16) - (cropTop + cropBottom) * cropUnitY
            if (width in 16..8192 && height in 16..8192) {
                H264Dimensions(width, height)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun removeEmulationPreventionBytes(src: ByteArray, start: Int, end: Int): ByteArray {
        val out = ArrayList<Byte>(end - start)
        var zeros = 0
        for (i in start until end) {
            val b = src[i].toInt() and 0xFF
            if (zeros == 2 && b == 0x03) {
                zeros = 0
                continue
            }
            out.add(b.toByte())
            zeros = if (b == 0) zeros + 1 else 0
        }
        return out.toByteArray()
    }

    private fun skipScalingList(br: BitReader, size: Int) {
        var lastScale = 8
        var nextScale = 8
        for (j in 0 until size) {
            if (nextScale != 0) {
                val deltaScale = br.readSE()
                nextScale = (lastScale + deltaScale + 256) % 256
            }
            lastScale = if (nextScale == 0) lastScale else nextScale
        }
    }

    private class BitReader(private val data: ByteArray) {
        private var byteOffset = 0
        private var bitOffset = 0

        fun readBit(): Int {
            if (byteOffset >= data.size) throw IllegalStateException("EOF")
            val bit = (data[byteOffset].toInt() shr (7 - bitOffset)) and 1
            bitOffset++
            if (bitOffset == 8) {
                bitOffset = 0
                byteOffset++
            }
            return bit
        }

        fun readBits(n: Int): Int {
            var res = 0
            for (i in 0 until n) {
                res = (res shl 1) or readBit()
            }
            return res
        }

        fun readUE(): Int {
            var leadingZeros = 0
            while (readBit() == 0) {
                leadingZeros++
                if (leadingZeros > 31) throw IllegalStateException("Invalid UE")
            }
            if (leadingZeros == 0) return 0
            return ((1 shl leadingZeros) - 1) + readBits(leadingZeros)
        }

        fun readSE(): Int {
            val ue = readUE()
            val sign = if ((ue and 1) == 0) -1 else 1
            return sign * ((ue + 1) shr 1)
        }
    }
}
