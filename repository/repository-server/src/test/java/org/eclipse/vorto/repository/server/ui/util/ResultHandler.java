/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.vorto.repository.server.ui.util;

/**
 * Represents the expected result from an action.<br/>
 * Implementors will perform the necessary verifications on the UI for both scenarios.
 */
public interface ResultHandler {

  void succeed();
  void fail();

}
