#pragma once

extern "C" {
#include <libavcodec/codec_id.h>
}

namespace clip {

// MediaCodec MIME types for the codecs AMediaCodec can plausibly accelerate.
// Anything else (e.g. mpeg1video) always falls back to the SW decoder.
inline const char *mediaCodecMimeFor(AVCodecID id) {
    switch (id) {
        case AV_CODEC_ID_H264: return "video/avc";
        case AV_CODEC_ID_HEVC: return "video/hevc";
        case AV_CODEC_ID_MPEG2VIDEO: return "video/mpeg2";
        case AV_CODEC_ID_MPEG4: return "video/mp4v-es";
        case AV_CODEC_ID_VP8: return "video/x-vnd.on2.vp8";
        case AV_CODEC_ID_VP9: return "video/x-vnd.on2.vp9";
        case AV_CODEC_ID_AV1: return "video/av01";
        default: return nullptr;
    }
}

} // namespace clip
