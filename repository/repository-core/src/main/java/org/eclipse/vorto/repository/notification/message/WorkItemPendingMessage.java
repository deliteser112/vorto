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
package org.eclipse.vorto.repository.notification.message;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.vorto.repository.account.User;
import org.eclipse.vorto.repository.core.ModelInfo;
import org.eclipse.vorto.repository.notification.INotificationService.NotificationProblem;

public class WorkItemPendingMessage extends AbstractMessage {

	private TemplateRenderer renderer;
	
	private ModelInfo modelInfo;
	
	public WorkItemPendingMessage(User recipient, ModelInfo modelInfo) {
		super(recipient);
		this.renderer = new TemplateRenderer("pending_workitem.ftl");
		this.modelInfo = modelInfo;
	}

	@Override
	public String getSubject() {
		return "[Vorto] Pending Approval for "+modelInfo.getId().getPrettyFormat();
	}

	@Override
	public String getContent() {
		Map<String,Object> ctx = new HashMap<>(1);
		ctx.put("user", recipient);
		ctx.put("model", modelInfo);
		try {
			return renderer.render(ctx);
		} catch (Exception e) {
			throw new NotificationProblem("Problem rendering notification message",e);
			
		}
	}

}