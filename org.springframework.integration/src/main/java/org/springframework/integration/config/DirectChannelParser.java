/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.config;

import org.w3c.dom.Element;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.integration.channel.config.AbstractChannelParser;
import org.springframework.integration.dispatcher.DirectChannel;
import org.springframework.util.StringUtils;

/**
 * Parser for the &lt;direct-channel&gt; element.
 * 
 * @author Mark Fisher
 */
public class DirectChannelParser extends AbstractChannelParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return DirectChannel.class;
	}

	@Override
	protected void configureConstructorArgs(BeanDefinitionBuilder builder, Element element) {
		String source = element.getAttribute("source");
		if (StringUtils.hasText(source)) {
			builder.addConstructorArgReference(source);
		}
	}

}
