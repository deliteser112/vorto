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
package
/*
 * generated by Xtext
 */
org.eclipse.vorto.editor.functionblock

import com.google.inject.Binder
import com.google.inject.Provides
import org.eclipse.vorto.editor.datatype.QualifiedNameWithVersionProvider
import org.eclipse.vorto.editor.datatype.converter.DatatypeValueConverter
import org.eclipse.vorto.editor.functionblock.formatting.FunctionblockFormatter
import org.eclipse.vorto.editor.functionblock.scoping.FunctionblockScopeProvider
import org.eclipse.vorto.editor.functionblock.validation.TypeFileAccessingHelper
import org.eclipse.vorto.editor.functionblock.validation.TypeHelper
import org.eclipse.xtext.conversion.IValueConverterService
import org.eclipse.xtext.naming.IQualifiedNameProvider
import org.eclipse.xtext.scoping.IScopeProvider
import org.eclipse.xtext.serializer.tokens.SerializerScopeProviderBinding

/** 
 * Use this class to register components to be used at runtime / without the
 * Equinox extension registry.
 */
class FunctionblockRuntimeModule extends AbstractFunctionblockRuntimeModule {
	override void configure(Binder binder) {
		super.configure(binder)
//		binder.bind(IOutputConfigurationProvider).to(FbOutputConfigurationProvider).in(Singleton)
	}

	override Class<? extends IScopeProvider> bindIScopeProvider() {
		return FunctionblockScopeProvider
	}

	@Provides def TypeHelper getTypeHelper() {
		return new TypeFileAccessingHelper()
	}

	override Class<? extends IQualifiedNameProvider> bindIQualifiedNameProvider() {
		return QualifiedNameWithVersionProvider
	}

	@SuppressWarnings("restriction") override void configureSerializerIScopeProvider(Binder binder) {
		binder.bind(IScopeProvider).annotatedWith(
			SerializerScopeProviderBinding).to(FunctionblockScopeProvider)
	}

	override Class<? extends IValueConverterService> bindIValueConverterService() {
		return DatatypeValueConverter
	}

	override bindIFormatter() {
		return FunctionblockFormatter
	}
}