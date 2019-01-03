/**
 * Copyright 2017-2019 European Union, interactive instruments GmbH
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * This work was supported by the EU Interoperability Solutions for
 * European Public Administrations Programme (http://ec.europa.eu/isa)
 * through Action 1.17: A Reusable INSPIRE Reference Platform (ARE3NA).
 */
package de.interactive_instruments.etf.testdriver.sui;

import static com.eviware.soapui.settings.HttpSettings.ENCODED_URLS;
import static de.interactive_instruments.etf.EtfConstants.ETF_DATA_STORAGE_NAME;
import static de.interactive_instruments.etf.sel.mapping.Types.SUI_SUPPORTED_TEST_OBJECT_TYPES;
import static de.interactive_instruments.etf.testdriver.sui.SuiTestDriver.SUI_TEST_DRIVER_EID;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.SoapUIExtensionClassLoader;

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
import de.interactive_instruments.etf.model.EID;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.testdriver.AbstractTestDriver;
import de.interactive_instruments.etf.testdriver.ComponentInitializer;
import de.interactive_instruments.etf.testdriver.TestTask;
import de.interactive_instruments.etf.testdriver.TestTaskInitializationException;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.ConfigProperties;

/**
 * SoapUI test driver component
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
@ComponentInitializer(id = SUI_TEST_DRIVER_EID)
public class SuiTestDriver extends AbstractTestDriver {

	public static final String SUI_TEST_DRIVER_EID = "4838e01b-4186-4d2d-a93a-414b9e9a49a7";
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

	public SuiTestDriver() {
		super(new ConfigProperties(EtfConstants.ETF_PROJECTS_DIR));
	}

	@Override
	public Collection<TestObjectTypeDto> getTestObjectTypes() {
		return SUI_SUPPORTED_TEST_OBJECT_TYPES.values();
	}

	@Override
	final public ComponentInfo getInfo() {
		return COMPONENT_INFO;
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
			return new SuiTestTask(testTaskDto);
		} catch (IncompleteDtoException e) {
			throw new TestTaskInitializationException(e);
		}

	}

	@Override
	final public void doInit()
			throws ConfigurationException, IllegalStateException, InitializationException, InvalidStateTransitionException {
		dataStorageCallback = DataStorageRegistry.instance().get(configProperties.getProperty(ETF_DATA_STORAGE_NAME));
		if (dataStorageCallback == null) {
			throw new InvalidStateTransitionException("Data Storage not set");
		}

		SoapUI.setSoapUICore(IISoapUICore.createDefault(), true);

		// Don't let SoapUI re-encode the URLs
		SoapUI.getSettings().setBoolean(ENCODED_URLS, true);

		propagateComponents();

		typeLoader = new SuiTypeLoader(dataStorageCallback);
		typeLoader.getConfigurationProperties().setPropertiesFrom(configProperties, true);
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
	public void doRelease() {}
}
