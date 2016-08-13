/**
 * Copyright (c) 2015-2016 Bosch Software Innovations GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * The Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Bosch Software Innovations GmbH - Please refer to git log
 */
/*
* generated by Xtext
*/
package org.eclipse.vorto.editor.functionblock;

import org.eclipse.xtext.junit4.IInjectorProvider;

import com.google.inject.Injector;

public class FunctionblockUiInjectorProvider implements IInjectorProvider {
	
	public Injector getInjector() {
		return org.eclipse.vorto.editor.functionblock.ui.internal.FunctionblockActivator.getInstance().getInjector("org.eclipse.vorto.fbeditor.Functionblock");
	}
	
}
