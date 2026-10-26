/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The SHA-256 of each page body of one run, to find a page that repeats an earlier page. The page
 * loop reads one body at a time: it reads the stream from {@link #hashing} to its end, then asks
 * {@link #earlierWithSameBody}.
 */
final class PageBodies {

  private final Map<String, Page> pagesByHash = new HashMap<>();
  private Supplier<String> currentHash;

  /** The body as a stream that hashes what the caller reads from it. */
  InputStream hashing(InputStream body) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java platform supports SHA-256", e);
    }
    currentHash = () -> HexFormat.of().formatHex(digest.digest());
    return new DigestInputStream(body, digest);
  }

  /**
   * Records the page under the hash of the body that the caller read through {@link #hashing}.
   *
   * @return the earlier page of the run with the same body
   */
  Optional<Page> earlierWithSameBody(Page page) {
    return Optional.ofNullable(pagesByHash.putIfAbsent(currentHash.get(), page));
  }
}
