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
package org.eclipse.vorto.repository.web.core.async;

import java.util.Collection;
import org.eclipse.vorto.model.ModelId;
import org.eclipse.vorto.repository.core.Attachment;

public class AsyncModelAttachmentsFetcher extends AsyncModelTaskRunner {

  private final ModelId id;
  private final Collection<Attachment> target;

  public AsyncModelAttachmentsFetcher(Collection<Attachment> target, ModelId id) {
    this.target = target;
    this.id = id;
  }

  @Override
  public void run() {
    super.run();
    target.addAll(factory.getRepositoryByModelWithoutSessionHelper(id).getAttachments(id));
  }
}
