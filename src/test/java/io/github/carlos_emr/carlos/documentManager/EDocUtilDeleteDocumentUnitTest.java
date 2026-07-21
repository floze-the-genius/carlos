/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EDocUtil")
@Tag("unit")
@Tag("documentManager")
class EDocUtilDeleteDocumentUnitTest extends CarlosUnitTestBase {

    private CtlDocumentDao ctlDocumentDao;
    private DemographicManager demographicManager;
    private DocumentDao documentDao;
    private LoggedInInfo loggedInInfo;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private ProviderDao providerDao;

    @BeforeEach
    void setUp() throws Exception {
        clearCachedDocumentDao();
        ctlDocumentDao = mock(CtlDocumentDao.class);
        demographicManager = mock(DemographicManager.class);
        documentDao = mock(DocumentDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        providerDao = mock(ProviderDao.class);
        registerMock(CtlDocumentDao.class, ctlDocumentDao);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DocumentDao.class, documentDao);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);
        registerMock(ProviderDao.class, providerDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        clearCachedDocumentDao();
    }

    @Test
    @DisplayName("should soft-delete document when it is not an active outbound email archive eDoc")
    void shouldSoftDeleteDocument_whenNotActiveOutboundEmailArchiveEdoc() {
        Document document = activeDocument();
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsActiveByDocumentNo(321)).thenReturn(false);

        EDocUtil.deleteDocument("321");

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_DELETED);
        assertThat(document.getUpdatedatetime()).isNotNull();
        verify(outboundEmailArchiveDao).existsActiveByDocumentNo(321);
        verify(documentDao).merge(document);
    }

    @Test
    @DisplayName("should reject normal delete when document backs an active outbound email archive")
    void shouldRejectDelete_whenDocumentBacksActiveOutboundEmailArchive() {
        Document document = activeDocument();
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsActiveByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.deleteDocument("321"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controlled archive deletion workflow");

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_ACTIVE);
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should not query outbound archive metadata when document does not exist")
    void shouldNotQueryOutboundArchiveMetadata_whenDocumentDoesNotExist() {
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(null);

        EDocUtil.deleteDocument("321");

        verifyNoInteractions(outboundEmailArchiveDao);
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should restore active status when undelete ctl status is empty")
    void shouldRestoreActiveStatus_whenUndeleteCtlStatusIsEmpty() {
        Document document = deletedDocument();
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setStatus("");
        when(ctlDocumentDao.getCtrlDocument(321)).thenReturn(ctlDocument);
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);

        EDocUtil.undeleteDocument("321");

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_ACTIVE);
        assertThat(document.getUpdatedatetime()).isNotNull();
        verify(documentDao).merge(document);
    }

    @Test
    @DisplayName("should format provider name when one name part is missing")
    void shouldFormatProviderName_whenOneNamePartIsMissing() {
        Provider provider = new Provider();
        provider.setFirstName("Alex");
        provider.setLastName(null);
        when(providerDao.getProvider("12")).thenReturn(provider);

        String name = EDocUtil.getProviderName("12");

        assertThat(name).isEqualTo("ALEX");
    }

    @Test
    @DisplayName("should include alias when demographic name parts are missing")
    void shouldIncludeAlias_whenDemographicNamePartsAreMissing() {
        Demographic demographic = new Demographic(123);
        demographic.setFirstName(null);
        demographic.setLastName(null);
        demographic.setAlias("Lex");
        when(demographicManager.getDemographic(loggedInInfo, "123")).thenReturn(demographic);

        String name = EDocUtil.getDemographicName(loggedInInfo, "123");

        assertThat(name).isEqualTo("(Lex)");
    }

    @Test
    @DisplayName("should return N/A when demographic name and alias are missing")
    void shouldReturnNotAvailable_whenDemographicNameAndAliasAreMissing() {
        Demographic demographic = new Demographic(123);
        demographic.setFirstName(" ");
        demographic.setLastName(null);
        demographic.setAlias(" ");
        when(demographicManager.getDemographic(loggedInInfo, "123")).thenReturn(demographic);

        String name = EDocUtil.getDemographicName(loggedInInfo, "123");

        assertThat(name).isEqualTo("N/A");
    }

    private Document activeDocument() {
        Document document = new Document();
        document.setDocumentNo(321);
        document.setStatus(Document.STATUS_ACTIVE);
        return document;
    }

    private Document deletedDocument() {
        Document document = new Document();
        document.setDocumentNo(321);
        document.setStatus(Document.STATUS_DELETED);
        return document;
    }

    private void clearCachedDocumentDao() throws Exception {
        Field documentDaoField = EDocUtil.class.getDeclaredField("documentDao");
        documentDaoField.setAccessible(true);
        documentDaoField.set(null, null);
    }
}
