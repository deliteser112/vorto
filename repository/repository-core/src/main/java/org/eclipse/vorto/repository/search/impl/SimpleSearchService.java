/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.vorto.repository.search.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.vorto.model.ModelId;
import org.eclipse.vorto.repository.core.IModelRepository;
import org.eclipse.vorto.repository.core.IModelRepositoryFactory;
import org.eclipse.vorto.repository.core.ModelInfo;
import org.eclipse.vorto.repository.search.IIndexingService;
import org.eclipse.vorto.repository.search.ISearchService;
import org.eclipse.vorto.repository.search.IndexingResult;
import org.eclipse.vorto.repository.tenant.ITenantService;

/**
 * Simple search which merely delegates the search to the model repository
 *
 */
public class SimpleSearchService implements ISearchService, IIndexingService {

  private ITenantService tenantService;

  private IModelRepositoryFactory repositoryFactory;

  public SimpleSearchService(ITenantService tenantService,
      IModelRepositoryFactory repositoryFactory) {
    this.tenantService = tenantService;
    this.repositoryFactory = repositoryFactory;
  }

  @Override
  public List<ModelInfo> search(Optional<Collection<String>> tenantIds, String expression) {
    List<ModelInfo> result = new ArrayList<>();
    
    if (tenantIds.isPresent() && !tenantIds.get().isEmpty()) {     
      tenantIds.get().stream().forEach(tenantId -> {
        IModelRepository repository = this.repositoryFactory.getRepository(tenantId);
        result.addAll(repository.search(expression));
      });
    } else {
      tenantService.getTenants().forEach(tenant -> {
        IModelRepository repository = this.repositoryFactory.getRepository(tenant.getTenantId());
        result.addAll(repository.search(expression));
      });
    }
    
    return result;
    
  }

  @Override
  public List<ModelInfo> search(String expression) {
    return search(Optional.empty(), expression);
  }

  @Override
  public IndexingResult reindexAllModels() {
    return new IndexingResult();
  }

  @Override
  public void indexModel(ModelInfo modelInfo, String tenantId) {
    // NOOP
  }

  @Override
  public void updateIndex(ModelInfo modelInfo) {
    // NOOP 
  }

  @Override
  public void deleteIndex(ModelId modelId) {
    // NOOP
  }

  @Override
  public void deleteIndexForTenant(String tenantId) {
    // NOOP
  }

}
