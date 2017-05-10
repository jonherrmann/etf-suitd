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

import static de.interactive_instruments.etf.EtfConstants.ETF_DATA_STORAGE_NAME;
import static de.interactive_instruments.etf.testdriver.sui.SuiTestDriver.SUI_TEST_DRIVER_EID;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.SoapUIExtensionClassLoader;

import de.interactive_instruments.CLUtils;
import de.interactive_instruments.IFile;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.DataStorageRegistry;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.IncompleteDtoException;
import de.interactive_instruments.etf.dal.dto.capabilities.ComponentDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.result.TestTaskResultDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.testdriver.*;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;
import de.interactive_instruments.xtf.SOAPUI_I;

/**
 * SoapUI test driver component
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
@ComponentInitializer(id = SUI_TEST_DRIVER_EID)
public class SuiTestDriver implements TestDriver {

	public static final String SUI_TEST_DRIVER_EID = "4838e01b-4186-4d2d-a93a-414b9e9a49a7";
	final private ConfigProperties configProperties = new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR);
	private SuiTypeLoader typeLoader;
	private DataStorage dataStorageCallback;
	private boolean pluginsInitialized = false;

	final static ComponentInfo COMPONENT_INFO = new ComponentInfo() {
		@Override
		public String getName() {
			return "SoapUI test driver";
		}

		@Override
		public EID getId() {
			return EidFactory.getDefault().createAndPreserveStr(SUI_TEST_DRIVER_EID);
		}

		@Override
		public String getVersion() {
			return this.getClass().getPackage().getImplementationVersion();
		}

		@Override
		public String getVendor() {
			return this.getClass().getPackage().getImplementationVendor();
		}

		@Override
		public String getDescription() {
			return "Test driver for SoapUI " + SoapUI.class.getPackage().getImplementationVersion();
		}
	};

	@Override
	public Collection<ExecutableTestSuiteDto> getExecutableTestSuites() {
		return typeLoader.getExecutableTestSuites();
	}

	@Override
	public Collection<TestObjectTypeDto> getTestObjectTypes() {
		return typeLoader.getTestObjectTypes();
	}

	@Override
	final public ComponentInfo getInfo() {
		return COMPONENT_INFO;
	}

	@Override
	public void lookupExecutableTestSuites(final EtsLookupRequest etsLookupRequest) {
		final Set<EID> etsIds = etsLookupRequest.getUnknownEts();
		final Set<ExecutableTestSuiteDto> knownEts = new HashSet<>();
		for (final EID etsId : etsIds) {
			final ExecutableTestSuiteDto ets = typeLoader.getExecutableTestSuiteById(etsId);
			if (ets != null) {
				knownEts.add(ets);
			}
		}
		etsLookupRequest.addKnownEts(knownEts);
	}

	@Override
	public TestTask createTestTask(final TestTaskDto testTaskDto) throws TestTaskInitializationException {
		try {
			Objects.requireNonNull(testTaskDto, "Test Task not set").ensureBasicValidity();

			// Get ETS
			testTaskDto.getTestObject().ensureBasicValidity();
			testTaskDto.getExecutableTestSuite().ensureBasicValidity();
			final TestTaskResultDto testTaskResult = new TestTaskResultDto();
			testTaskResult.setId(EidFactory.getDefault().createRandomId());
			testTaskDto.setTestTaskResult(testTaskResult);
			return new SuiTestTask(testTaskDto, dataStorageCallback);
		} catch (IncompleteDtoException e) {
			throw new TestTaskInitializationException(e);
		}

	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return this.configProperties;
	}

	@Override
	final public void init()
			throws ConfigurationException, IllegalStateException, InitializationException, InvalidStateTransitionException {
		configProperties.expectAllRequiredPropertiesSet();
		dataStorageCallback = DataStorageRegistry.instance().get(configProperties.getProperty(ETF_DATA_STORAGE_NAME));
		if (dataStorageCallback == null) {
			throw new InvalidStateTransitionException("Data Storage not set");
		}

		SoapUI.setSoapUICore(IISoapUICore.createDefault(), true);

		propagateComponents();

		typeLoader = new SuiTypeLoader(dataStorageCallback);
		typeLoader.getConfigurationProperties().setPropertiesFrom(configProperties, true);
		typeLoader.init();
	}

	private void initPlugins(final IFile pluginDir) {

		if (pluginsInitialized) {
			return;
		}

		for (File pluginFile : pluginDir.listFiles()) {
			if (!pluginFile.getName().toLowerCase().endsWith("-plugin.jar")) {
				continue;
			}

			JarFile jarFile = null;
			try {
				if (SoapUI.getSoapUICore() == null) {
					SoapUI.setSoapUICore(IISoapUICore.createDefault(), true);
				}
				SoapUIExtensionClassLoader extClassLoader = SoapUI.getSoapUICore().getExtensionClassLoader();

				// add jar to our extension classLoader
				extClassLoader.addFile(pluginFile);
				jarFile = new JarFile(pluginFile);

				// look for factories
				JarEntry entry = jarFile.getJarEntry("META-INF/factories.xml");
				if (entry != null)
					SoapUI.getFactoryRegistry().addConfig(jarFile.getInputStream(entry), extClassLoader);

				// look for listeners
				entry = jarFile.getJarEntry("META-INF/listeners.xml");
				if (entry != null)
					SoapUI.getListenerRegistry().addConfig(jarFile.getInputStream(entry), extClassLoader);
				IFile.closeQuietly(jarFile);
			} catch (Exception e) {
				IFile.closeQuietly(jarFile);
				e.printStackTrace();
			}
		}
		pluginsInitialized = true;
	}

	private void propagateComponents() throws InitializationException {
		// Propagate Component COMPONENT_INFO from here
		final WriteDao<ComponentDto> componentDao = ((WriteDao<ComponentDto>) dataStorageCallback.getDao(ComponentDto.class));
		try {
			try {
				componentDao.delete(this.getInfo().getId());
			} catch (ObjectWithIdNotFoundException e) {
				ExcUtils.suppress(e);
			}
			componentDao.add(new ComponentDto(this.getInfo()));
		} catch (StorageException e) {
			throw new InitializationException(e);
		}
	}

	@Override
	public boolean isInitialized() {
		return dataStorageCallback != null && typeLoader != null && typeLoader.isInitialized();
	}

	@Override
	public void release() {
		typeLoader.release();
	}
}
