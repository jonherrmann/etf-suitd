/**
 * Copyright 2010-2017 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.testdriver.sui;

import static de.interactive_instruments.etf.sel.mapping.Types.SUI_SUPPORTED_TEST_OBJECT_TYPES;
import static de.interactive_instruments.etf.sel.mapping.Types.TEST_ITEM_TYPES;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.Dto;
import de.interactive_instruments.etf.dal.dto.capabilities.TagDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.dal.dto.test.TestItemTypeDto;
import de.interactive_instruments.etf.dal.dto.translation.TranslationTemplateBundleDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.testdriver.EtsTypeLoader;
import de.interactive_instruments.etf.testdriver.TypeBuildingFileVisitor;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.InvalidStateTransitionException;
import de.interactive_instruments.exceptions.StorageException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class SuiTypeLoader extends EtsTypeLoader {

	// Supported Test Object Types

	private final ConfigProperties configProperties;

	/**
	 * Default constructor.
	 */
	public SuiTypeLoader(final DataStorage dataStorage) {
		super(dataStorage, new ArrayList<TypeBuildingFileVisitor.TypeBuilder<? extends Dto>>() {
			{
				add(new SuiEtsBuilder(dataStorage.getDao(ExecutableTestSuiteDto.class),
						dataStorage.getDao(TranslationTemplateBundleDto.class),
						dataStorage.getDao(TagDto.class)));
			}
		});
		this.configProperties = new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR);
	}

	@Override
	public void doInit()
			throws ConfigurationException, InitializationException, InvalidStateTransitionException {

		this.configProperties.expectAllRequiredPropertiesSet();
		this.watchDir = configProperties.getPropertyAsFile(EtfConstants.ETF_PROJECTS_DIR);
		try {
			this.watchDir.expectDirIsReadable();
		} catch (IOException e) {
			throw new InitializationException(e);
		}

		// First propagate static types
		final WriteDao<TestItemTypeDto> testItemTypeDao = ((WriteDao<TestItemTypeDto>) dataStorageCallback
				.getDao(TestItemTypeDto.class));
		try {
			testItemTypeDao.deleteAllExisting(TEST_ITEM_TYPES.keySet());
			testItemTypeDao.addAll(TEST_ITEM_TYPES.values());
		} catch (final StorageException e) {
			try {
				testItemTypeDao.deleteAllExisting(TEST_ITEM_TYPES.keySet());
			} catch (StorageException e3) {
				ExcUtils.suppress(e3);
			}
			throw new InitializationException(e);
		}
	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return configProperties;
	}

	TestObjectTypeDto getTestObjectTypeById(final EID id) {
		return SUI_SUPPORTED_TEST_OBJECT_TYPES.get(id);
	}

	Collection<TestObjectTypeDto> getTestObjectTypes() {
		return SUI_SUPPORTED_TEST_OBJECT_TYPES.values();
	}
}
