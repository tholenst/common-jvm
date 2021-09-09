// Copyright 2021 The Cross-Media Measurement Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.wfanet.measurement.common.crypto.testing

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.wfanet.measurement.common.getRuntimePath

const val KEY_ALGORITHM = "EC"

/**
 * For some tests, we used a fixed certificate server.*. All the other certificates are generated by
 * bazel and are likely cached by bazel.
 */
private val FIXED_TESTDATA_DIR_PATH =
  Paths.get(
    "wfa_common_jvm",
    "src",
    "main",
    "kotlin",
    "org",
    "wfanet",
    "measurement",
    "common",
    "crypto",
    "testing",
    "testdata"
  )
val FIXED_SERVER_CERT_PEM_FILE = loadTestFixedResourceFile("server.pem")
val FIXED_SERVER_KEY_FILE = loadTestFixedResourceFile("server.key")
val FIXED_SERVER_CERT_DER_FILE = loadTestFixedResourceFile("server-cert.der")
val FIXED_SERVER_KEY_DER_FILE = loadTestFixedResourceFile("server-key.der")
val FIXED_CA_CERT_PEM_FILE = loadTestFixedResourceFile("ca.pem")
val FIXED_ENCRYPTION_PRIVATE_KEY_DER_FILE = loadTestFixedResourceFile("ec-private.der")
val FIXED_ENCRYPTION_PUBLIC_KEY_DER_FILE = loadTestFixedResourceFile("ec-public.der")

/**
 * Certificates generated by bazel
 */
private val TEST_DATA_DIR_PATH =
  Paths.get(
    "wfa_common_jvm",
    "build",
    "openssl",
  )
val ORG1_SERVER_CERT_PEM_FILE = loadTestResourceFile("test_root.pem")
val ORG1_SERVER_CERT_KEY_FILE = loadTestResourceFile("test_root.key")
val ORG2_SERVER_CERT_PEM_FILE = loadTestResourceFile("test_root_2.pem")
val ORG2_SERVER_CERT_KEY_FILE = loadTestResourceFile("test_root_2.key")

fun loadTestFixedResourceFile(filename: String): File =
  checkNotNull(getRuntimePath(FIXED_TESTDATA_DIR_PATH.resolve(filename))?.toFile())
  { "Test resource file not found: $filename" }

fun loadTestResourceFile(filename: String): File = TEST_DATA_DIR_PATH.resolve(filename).toFile()
