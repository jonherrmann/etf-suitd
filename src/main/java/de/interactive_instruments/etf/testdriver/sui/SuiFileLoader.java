/**
 * Copyright 2017-2020 European Union, interactive instruments GmbH
 *
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

import static de.interactive_instruments.etf.EtfConstants.ETF_TRANSLATION_TEMPLATE_BUNDLE_ID_PK;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.project.ProjectFactoryRegistry;
import com.eviware.soapui.support.SoapUIException;

import org.apache.xmlbeans.XmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.interactive_instruments.LogUtils;
import de.interactive_instruments.SUtils;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.component.loaders.AbstractItemFileLoader;
import de.interactive_instruments.etf.component.loaders.ItemFileLoaderResultListener;
import de.interactive_instruments.etf.dal.dao.Dao;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.StreamWriteDao;
import de.interactive_instruments.etf.dal.dto.capabilities.ComponentDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TagDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.dal.dto.translation.TranslationTemplateBundleDto;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.model.ParameterSet;
import de.interactive_instruments.etf.sel.mapping.EtsMapper;
import de.interactive_instruments.exceptions.ExcUtils;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import de.interactive_instruments.exceptions.StorageException;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class SuiFileLoader extends AbstractItemFileLoader<ExecutableTestSuiteDto> {

    private final static Logger logger = LoggerFactory.getLogger(SuiFileLoader.class);
    private final StreamWriteDao<ExecutableTestSuiteDto> writeDao;
    private final Dao<TranslationTemplateBundleDto> translationTemplateBundleDao;
    private final Dao<TagDto> tagDao;
    private WsdlProject project;

    SuiFileLoader(final ItemFileLoaderResultListener<ExecutableTestSuiteDto> loaderResultListener,
            final Path path,
            final DataStorage dataStorageCallback) {
        super(loaderResultListener, 400, path.toFile());
        this.writeDao = (StreamWriteDao<ExecutableTestSuiteDto>) dataStorageCallback.getDao(ExecutableTestSuiteDto.class);
        this.translationTemplateBundleDao = dataStorageCallback.getDao(TranslationTemplateBundleDto.class);
        this.tagDao = dataStorageCallback.getDao(TagDto.class);
    }

    @Override
    protected boolean doPrepare() {
        try {
            this.project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory(
                    "wsdl").createNew(this.file.getPath());
            final String translationTemplateId = project.getPropertyValue(SuiConstants.TRANSLATION_TEMPLATE_ID_PROPERTY);
            if (!SUtils.isNullOrEmpty(translationTemplateId)) {
                dependsOn(EidFactory.getDefault().createUUID(translationTemplateId));
            }
            final String tagIds = project.getPropertyValue(SuiConstants.TAG_IDS_PROPERTY);
            if (!SUtils.isNullOrEmpty(tagIds)) {
                final String[] t = tagIds.split(",");
                for (final String s : t) {
                    dependsOn(EidFactory.getDefault().createUUID(s.trim()));
                }
            }
            final String dependencyIds = project.getPropertyValue(SuiConstants.DEPENDENCY_IDS_PROPERTY);
            if (!SUtils.isNullOrEmpty(dependencyIds)) {
                final String[] deps = dependencyIds.split(",");
                for (final String d : deps) {
                    dependsOn(EidFactory.getDefault().createUUID(d.trim()));
                }
            }
        } catch (XmlException | IOException | SoapUIException e) {
            return false;
        }
        return true;
    }

    @Override
    protected ExecutableTestSuiteDto doBuild() {
        try {
            final ExecutableTestSuiteDto executableTestSuiteDto = new EtsMapper(project).toTestTaskResult();
            executableTestSuiteDto.setTestDriver(new ComponentDto(SuiTestDriver.COMPONENT_INFO));

            final ParameterSet parameters = new ParameterSet();
            final String ignoreProperty = project.getPropertyValue("etf.ignore.properties");
            final Set<String> ignoreParameteres;
            if (!SUtils.isNullOrEmpty(ignoreProperty)) {
                ignoreParameteres = new HashSet<>(EtfConstants.ETF_PROPERTY_KEYS);
                final String[] p = ignoreProperty.split(",");
                for (final String s : p) {
                    ignoreParameteres.add(s.trim());
                }
            } else {
                ignoreParameteres = EtfConstants.ETF_PROPERTY_KEYS;
            }

            if (!SUtils.isNullOrEmpty(executableTestSuiteDto.getReference())) {
                // TODO set or get from repository adapter
                try {
                    executableTestSuiteDto.setRemoteResource(new URI(executableTestSuiteDto.getReference()));
                } catch (URISyntaxException e) {
                    logger.error(LogUtils.FATAL_MESSAGE, "Invalid reference {}",
                            executableTestSuiteDto.getReference(), e);
                    executableTestSuiteDto.setRemoteResource(URI.create("http://none"));
                }
            } else {
                executableTestSuiteDto.setRemoteResource(URI.create("http://none"));
            }

            final String translationTemplateId = project.getPropertyValue(SuiConstants.TRANSLATION_TEMPLATE_ID_PROPERTY);
            if (!SUtils.isNullOrEmpty(translationTemplateId)) {
                try {
                    final TranslationTemplateBundleDto translationTemplateBundleDto = translationTemplateBundleDao.getById(
                            EidFactory.getDefault().createUUID(translationTemplateId)).getDto();
                    executableTestSuiteDto.setTranslationTemplateBundle(translationTemplateBundleDto);
                } catch (ObjectWithIdNotFoundException e) {
                    logger.error(LogUtils.FATAL_MESSAGE,
                            "Could not load Translation Template Bundle for Executable Test Suite {}",
                            executableTestSuiteDto.getDescriptiveLabel(), e);
                }
            }

            final String tagIds = project.getPropertyValue(SuiConstants.TAG_IDS_PROPERTY);
            if (!SUtils.isNullOrEmpty(tagIds)) {
                try {
                    final String[] t = tagIds.split(",");
                    for (final String s : t) {
                        final TagDto tagDto = tagDao.getById(
                                EidFactory.getDefault().createUUID(s.trim())).getDto();
                        executableTestSuiteDto.addTag(tagDto);
                    }
                } catch (ObjectWithIdNotFoundException e) {
                    logger.error(LogUtils.FATAL_MESSAGE, "Could not load Tag for Executable Test Suite {}",
                            executableTestSuiteDto.getDescriptiveLabel(), e);
                }
            }

            project.getPropertyList().stream().filter(
                    property -> !"serviceEndpoint".equals(property.getName())
                            && !SuiConstants.TRANSLATION_TEMPLATE_ID_PROPERTY.equals(property.getName())
                            && !SuiConstants.TAG_IDS_PROPERTY.equals(property.getName())
                            && !ignoreParameteres.contains(property.getName()))
                    .forEach(property -> parameters.addParameter(property.getName(), property.getValue()));
            executableTestSuiteDto.setParameters(parameters);
            if (project.hasProperty(ETF_TRANSLATION_TEMPLATE_BUNDLE_ID_PK)) {
                final String id = project.getPropertyValue(ETF_TRANSLATION_TEMPLATE_BUNDLE_ID_PK);
                executableTestSuiteDto.setTranslationTemplateBundle(
                        translationTemplateBundleDao.getById(EidFactory.getDefault().createAndPreserveStr(id)).getDto());
            }

            final String dependencyIds = project.getPropertyValue(SuiConstants.DEPENDENCY_IDS_PROPERTY);
            if (!SUtils.isNullOrEmpty(dependencyIds)) {
                final String[] deps = dependencyIds.split(",");
                for (final String d : deps) {
                    executableTestSuiteDto.addDependency(
                            (ExecutableTestSuiteDto) getDependency(EidFactory.getDefault().createUUID(d.trim())));
                }
            }

            // release resources
            project.release();

            writeDao.add(executableTestSuiteDto);
            return executableTestSuiteDto;
        } catch (StorageException | ObjectWithIdNotFoundException e) {
            logger.error("Error creating Executable Test Suite from file {}", this.file.toPath(), e);
        }
        return null;
    }

    @Override
    protected void doRelease() {
        if (getResult() != null) {
            try {
                writeDao.delete(getResult().getId());
            } catch (StorageException | ObjectWithIdNotFoundException e) {
                ExcUtils.suppress(e);
            }
        }
    }
}
