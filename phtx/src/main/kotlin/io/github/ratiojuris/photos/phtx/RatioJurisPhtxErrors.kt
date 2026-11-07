// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 RatioJuris
package io.github.ratiojuris.photos.phtx

/**
 * All failures raised while reading or writing a protected container.
 * Messages are intentionally free of key material and passwords.
 */
sealed class RatioJurisPhtxException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The data is not a PHTX container (magic mismatch). */
    class NotProtectedImage : RatioJurisPhtxException("Not a protected image")

    /** Structurally invalid: bad lengths, offsets, reserved bits, truncation, trailing bytes. */
    class Malformed(detail: String) : RatioJurisPhtxException("Malformed protected image: $detail")

    /** Valid magic but a version/algorithm this implementation does not understand. */
    class Unsupported(detail: String) : RatioJurisPhtxException("Unsupported protected image: $detail")

    /** The password-derived key failed to authenticate the wrapped content key. */
    class WrongPassword : RatioJurisPhtxException("Incorrect password")

    /** The key unwrapped but the payload failed authentication (tampered or corrupted). */
    class Corrupted(cause: Throwable? = null) : RatioJurisPhtxException("Protected image is damaged or was modified", cause)

    /** The image to protect contains no data. */
    class EmptyInput : RatioJurisPhtxException("The image is empty")

    /** Input exceeds an implementation or format limit. */
    class TooLarge(detail: String) : RatioJurisPhtxException("Too large: $detail")
}
