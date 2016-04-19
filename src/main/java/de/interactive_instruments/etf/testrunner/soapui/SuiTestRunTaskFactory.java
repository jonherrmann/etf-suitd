/**
 * Copyright 2010-2016 interactive instruments GmbH
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
package de.interactive_instruments.etf.testrunner.soapui;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.eviware.soapui.DefaultSoapUICore;
import com.eviware.soapui.IISoapUICore;
import com.eviware.soapui.SoapUI;
import com.eviware.soapui.SoapUIExtensionClassLoader;
import com.eviware.soapui.impl.wsdl.support.xsd.SchemaUtils;

import org.apache.xmlbeans.XmlBeans;

import de.interactive_instruments.IFile;
import de.interactive_instruments.concurrent.InvalidStateTransitionException;
import de.interactive_instruments.container.ContainerFactory;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.dal.assembler.AssemblerException;
import de.interactive_instruments.etf.dal.dao.TestProjectDao;
import de.interactive_instruments.etf.dal.dto.plan.TestRunDto;
import de.interactive_instruments.etf.dal.dto.result.TestReportDto;
import de.interactive_instruments.etf.driver.TestProjectUnavailable;
import de.interactive_instruments.etf.driver.TestRunTask;
import de.interactive_instruments.etf.driver.TestRunTaskFactory;
import de.interactive_instruments.etf.model.plan.TestProject;
import de.interactive_instruments.etf.model.result.TestReport;
import de.interactive_instruments.etf.sel.model.impl.result.SuiTestReport;
import de.interactive_instruments.etf.sel.model.mapping.SuiTestObject;
import de.interactive_instruments.etf.sel.model.mapping.SuiTestObjectAssembler;
import de.interactive_instruments.etf.sel.model.mapping.SuiTestProject;
import de.interactive_instruments.etf.sel.model.mapping.SuiTestReportAssembler;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import de.interactive_instruments.exceptions.StoreException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.exceptions.config.MissingPropertyException;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.ConfigPropertyHolder;
import de.interactive_instruments.properties.PropertyHolder;

/**
 * SoapUI TestRunTaskFactory
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class SuiTestRunTaskFactory implements TestRunTaskFactory {

	private SuiTestProjectDao store;
	private IFile projectDir;
	private boolean pluginsInitialized = false;
	private final ConfigProperties configProperties;

	public SuiTestRunTaskFactory() {
		this.configProperties = new ConfigProperties(SuiConstants.PROJECT_DIR_KEY);
	}

	@Override
	public void init() throws ConfigurationException, InvalidStateTransitionException, InitializationException {
		try {
			String projDir = configProperties.getProperty(SuiConstants.SUI_PROJECT_DIR_KEY);
			if (projDir == null || projDir.isEmpty()) {
				projDir = configProperties.getProperty(SuiConstants.PROJECT_DIR_KEY);
				if (projDir == null || projDir.isEmpty()) {
					throw new MissingPropertyException(SuiConstants.PROJECT_DIR_KEY + " or " +
							SuiConstants.SUI_PROJECT_DIR_KEY);
				} else {
					this.projectDir = new IFile((projDir),
							"SoapUI PROJECT_DIR").expandPath("sui");
				}
			} else {
				this.projectDir = new IFile((projDir),
						"SoapUI PROJECT_DIR");
			}
			this.projectDir.ensureDir();
			this.projectDir.expectDirIsReadable();
			this.store = new SuiTestProjectDao();
			this.store.getConfigurationProperties().setPropertiesFrom(configProperties, true);
			this.store.init();

			SoapUI.setSoapUICore(IISoapUICore.createDefault(), true);

			// Configure plugin dir
			String pluginPath = configProperties.getProperty("etf.runner.sui.plugins");
			IFile pluginDir = null;
			if (pluginPath == null) {
				//Try default in td/sui/plugins
				pluginPath = configProperties.getProperty(EtfConstants.ETF_TESTDRIVERS_DIR);
				if (pluginPath != null) {
					pluginDir = new IFile(pluginPath, "TESTDRIVERS DIR").expandPath(
							"sui/plugins");
					if (!pluginDir.exists()) {
						pluginDir = null;
					}
				}
			} else {
				pluginDir = new IFile(pluginPath, "SUI PLUGINS");
			}
			if (pluginDir != null) {
				pluginDir.expectDirIsReadable();
				initPlugins(pluginDir);
			}
		} catch (IOException e) {
			throw new ConfigurationException("Invalid configuration: " + e.getMessage());
		}
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
				SoapUI.getSoapUICore().getExtensionClassLoader().addFile(pluginFile);
				jarFile = new JarFile(pluginFile);

				// look for factories
				JarEntry entry = jarFile.getJarEntry("META-INF/factories.xml");
				if (entry != null)
					SoapUI.getFactoryRegistry().addConfig(jarFile.getInputStream(entry), extClassLoader);

				// look for listeners
				entry = jarFile.getJarEntry("META-INF/listeners.xml");
				if (entry != null)
					SoapUI.getListenerRegistry().addConfig(jarFile.getInputStream(entry), extClassLoader);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		pluginsInitialized = true;
	}

	@Override
	public ConfigPropertyHolder getConfigurationProperties() {
		return this.configProperties;
	}

	@Override
	public boolean isInitialized() {
		return this.pluginsInitialized;
	}

	@Override
	public TestRunTask createTestRunTask(TestRunDto testRunDto) throws ConfigurationException {

		try {
			final SuiTestProject project = (SuiTestProject) getTestProjectStore().getById(
					testRunDto.getTestProject().getId());
			final SuiTestObject testObject = new SuiTestObjectAssembler().assembleEntity(
					testRunDto.getTestObject());
			final SuiTestReport report = new SuiTestReport(
					testRunDto.getTestReport().getContainerFactory(),
					testRunDto.getUsernameOfInitiator(),
					testRunDto.getTestReport().getPublicationLocation(),
					testRunDto.getTestReport().getId(),
					testRunDto.getLabel(),
					testObject);
			final SuiTestRun testRun = new SuiTestRun(testRunDto, testObject, report, project);
			return new SuiTestRunTask(testRun);
		} catch (StoreException | IOException | ObjectWithIdNotFoundException | AssemblerException e) {
			throw new ConfigurationException(e.getMessage());
		}
	}

	@Override
	public TestProjectDao getTestProjectStore() throws ConfigurationException {
		return store;
	}

	@Override
	public void release() {
		try {
			SoapUI.getThreadPool().shutdown();
			SoapUI.shutdown();
		} catch (Exception e) {
			ExcUtils.supress(e);
		}
		store.release();
	}

	@Override
	public String getTestDriverId() {
		return "SUI";
	}
}
