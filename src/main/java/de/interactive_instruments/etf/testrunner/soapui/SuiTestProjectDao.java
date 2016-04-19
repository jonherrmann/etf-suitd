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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.*;
import java.util.regex.Pattern;

import com.eviware.soapui.config.ProjectConfig;
import com.eviware.soapui.config.SoapuiProjectDocumentConfig;
import com.eviware.soapui.impl.wsdl.support.wsdl.UrlWsdlLoader;

import de.interactive_instruments.IFile;
import de.interactive_instruments.UriUtils;
import de.interactive_instruments.concurrent.InvalidStateTransitionException;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.dal.ProjectDtoFileBuilder;
import de.interactive_instruments.etf.dal.ProjectFileBuildVisitor;
import de.interactive_instruments.etf.dal.dao.AbstractTestProjectDao;
import de.interactive_instruments.etf.dal.dto.plan.TestProjectDto;
import de.interactive_instruments.etf.dal.dto.plan.TestProjectDtoBuilder;
import de.interactive_instruments.etf.model.item.EID;
import de.interactive_instruments.etf.model.item.EidDefaultMap;
import de.interactive_instruments.etf.model.item.EidFactory;
import de.interactive_instruments.etf.model.plan.AbstractTestObjectResourceType;
import de.interactive_instruments.etf.model.plan.TestObjectResourceType;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import de.interactive_instruments.exceptions.StoreException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.exceptions.config.MissingPropertyException;
import de.interactive_instruments.io.FileChangeListener;
import de.interactive_instruments.io.RecursiveDirWatcher;
import de.interactive_instruments.properties.ConfigProperties;
import de.interactive_instruments.properties.Properties;

/**
 * Data access object which manages the SoapUI ETS in a directory
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class SuiTestProjectDao extends AbstractTestProjectDao implements FileChangeListener, ProjectDtoFileBuilder {

	private IFile projectDir;
	private RecursiveDirWatcher watcher;
	private ProjectFileBuildVisitor visitor;
	private boolean initialized = false;

	@Override
	public void filesChanged(final Map<Path, WatchEvent.Kind> eventMap, final Set<Path> dirs) {
		dirs.forEach(d -> {
			logger.info("Watch service reports changes in directory: " + d.toString());
			try {
				Files.walkFileTree(d, visitor);
			} catch (IOException e) {
				logger.error("Failed to walk file tree: " + e.getMessage());
			}
		});
		cache.values().removeIf(p -> !UriUtils.exists(p.getUri()));
	}

	@Override
	public TestProjectDto createProjectDto(File projectFile) {
		try {
			logger.info("Registering project: {}", projectFile.getAbsolutePath());
			return buildProjectDto(new IFile(projectFile), this.resourceTypes);
		} catch (final StoreException e) {
			logger.error("Failed to create test project ({}): {}", projectFile.getAbsolutePath(), e.getMessage());
			return null;
		}
	}

	@Override
	public boolean accept(Path path) {
		return path.getFileName().toString().endsWith(SuiConstants.PROJECT_SUFFIX);
	}

	class WfsTestObjectResourceType extends AbstractTestObjectResourceType {

		// TODO: Rename serviceEnpoint to wfs_capabilites_url. Has to be changed in all test projects as well..
		WfsTestObjectResourceType() {
			super("serviceEndpoint", "WFS/WMS Capabilities URL of the Service", "http",
					Pattern.compile("^(http|https)://*.", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
					Pattern.compile("^(http|https)://*.(wfs).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
		}
	}

	class WmsTestObjectResourceType extends AbstractTestObjectResourceType {

		WmsTestObjectResourceType() {
			super("wms_capabilites_url", "WMS Capabilities URL of the Web Map Service", "http",
					Pattern.compile("^(http|https)://*.", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
					Pattern.compile("^(http|https)://*.(wms).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
		}
	}

	public SuiTestProjectDao() {
		configProperties = new ConfigProperties();
		this.assembler = new SuiTestProjectEntityAssembler();
		final WfsTestObjectResourceType wfsRes = new WfsTestObjectResourceType();
		this.resourceTypes.put(wfsRes.getId(), wfsRes);
		final WmsTestObjectResourceType wmsRes = new WmsTestObjectResourceType();
		this.resourceTypes.put(wmsRes.getId(), wmsRes);
	}

	private synchronized TestProjectDto buildProjectDto(final File file, final Map<EID, TestObjectResourceType> resourceTypes)
			throws StoreException {
		try {
			final TestProjectDtoBuilder b = new TestProjectDtoBuilder();
			final UrlWsdlLoader loader = new UrlWsdlLoader(file.toString());
			loader.setUseWorker(false);
			final SoapuiProjectDocumentConfig projectDocument = SoapuiProjectDocumentConfig.Factory.parse(loader.load());
			final ProjectConfig project = projectDocument.getSoapuiProject();

			b.setLabel(project.getName());
			b.setDescription(project.getDescription());
			b.setUri(file.toURI());
			final Map<String, String> properties = new TreeMap<String, String>();
			project.getProperties().getPropertyList().stream().filter(property -> !"serviceEndpoint".equals(property.getName())).forEach(property -> properties.put(property.getName(), property.getValue()));
			// Check for a property file
			final Properties projProperties = new Properties(properties);
			final IFile propertyFile = new IFile(
					file.getParentFile(),
					file.getName().replace(
							SuiConstants.PROJECT_SUFFIX,
							EtfConstants.ETF_TESTPROJECT_PROPERTY_FILE_SUFFIX));
			if (propertyFile.exists()) {
				projProperties.setPropertiesFrom(propertyFile, true);
			}
			b.setProperties(projProperties);
			b.setTestDriverId("SUI");
			b.setSupportedResourceTypes(new EidDefaultMap<TestObjectResourceType>() {
				{
					final TestObjectResourceType res = resourceTypes.get(EidFactory.getDefault().createFromStrAsStr("serviceEndpoint"));
					if (res == null) {
						throw new StoreException("TestObjectResourceType with ID \"+" + res.getId() + "\" not found");
					}
					put(res.getId(), res);
				}
			});
			b.setId(EidFactory.getDefault().createFromStrAsUUID(file.getAbsolutePath()));
			return b.createTestProjectDto();
		} catch (Exception e) {
			throw new StoreException(e.getMessage());
		}
	}

	@Override
	public List<TestProjectDto> getAll() throws StoreException {
		return new ArrayList<>(cache.values());
	}

	@Override
	public TestProjectDto getDtoByName(String s) throws StoreException {
		for (final TestProjectDto dto : cache.values()) {
			if (dto.getLabel().equals(s)) {
				return dto;
			}
		}
		return null;
	}

	@Override
	protected TestProjectDto cacheMissGetDtoById(EID eid) throws StoreException {
		return null;
	}

	@Override
	protected void doInit() throws ConfigurationException, InitializationException, InvalidStateTransitionException {
		if (initialized == true) {
			throw new InvalidStateTransitionException("Already initialized");
		}
		if (configProperties.hasProperty(EtfConstants.ETF_PROJECTS_DIR)) {
			this.projectDir = configProperties.getPropertyAsFile(EtfConstants.ETF_PROJECTS_DIR).expandPath("sui");
		} else if (configProperties.hasProperty(SuiConstants.SUI_PROJECT_DIR_KEY)) {
			this.projectDir = configProperties.getPropertyAsFile(SuiConstants.SUI_PROJECT_DIR_KEY).expandPath("sui");
		} else {
			throw new MissingPropertyException(EtfConstants.ETF_PROJECTS_DIR);
		}
		try {
			this.projectDir.expectDirIsReadable();
		} catch (IOException e) {
			throw new ConfigurationException(e.getMessage());
		}
		visitor = new ProjectFileBuildVisitor(this, cache);

		try {
			Files.walkFileTree(this.projectDir.toPath(), visitor);
		} catch (IOException e) {
			logger.error("Failed to walk file tree: " + e.getMessage());
		}

		watcher = RecursiveDirWatcher.create(this.projectDir.toPath(), this);
		try {
			watcher.start();
		} catch (IOException e) {
			logger.error("Failed to start watch service: " + e.getMessage());
			throw new InitializationException(e);
		}
		this.initialized = true;
	}

	@Override
	public void delete(EID id) throws StoreException, ObjectWithIdNotFoundException {
		// TODO read only dao
		throw new StoreException("Unimplemented");
	}

	@Override
	public void update(TestProjectDto dto) throws StoreException, ObjectWithIdNotFoundException {
		// TODO read only dao
		throw new StoreException("Unimplemented");
	}

	@Override
	public void doRelease() {
		this.cache.clear();
		this.watcher.release();
	}
}
