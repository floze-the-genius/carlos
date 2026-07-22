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

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("DocumentAttachmentManagerImpl")
@Tag("unit")
@Tag("documentManager")
class DocumentAttachmentManagerImplTest extends CarlosUnitTestBase {

    private DocumentAttachmentManagerImpl manager;
    private ConsultDocsDao consultDocsDao;
    private EFormDocsDao eFormDocsDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        manager = new DocumentAttachmentManagerImpl();
        consultDocsDao = mock(ConsultDocsDao.class);
        eFormDocsDao = mock(EFormDocsDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        injectDependency(manager, "consultDocsDao", consultDocsDao);
        injectDependency(manager, "eFormDocsDao", eFormDocsDao);
        injectDependency(manager, "outboundEmailArchiveDao", outboundEmailArchiveDao);
        injectDependency(manager, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should reject archive-backed documents before consult attachment persistence")
    void shouldRejectArchiveDocsBeforeConsultAttach() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 456)).thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.attachToConsult(loggedInInfo, DocumentType.DOC,
                new String[]{"321"}, "999998", 123, 456))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should reject archive-backed documents before Ocean consult attachment staging")
    void shouldRejectArchiveDocsBeforeOceanConsultAttach() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, 456)).thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.attachToConsult(loggedInInfo, DocumentType.DOC,
                new String[]{"321"}, "999998", 123, 456, true))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should reject archive-backed documents before eForm attachment persistence")
    void shouldRejectArchiveDocsBeforeEFormAttach() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.WRITE, 456)).thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.attachToEForm(loggedInInfo, DocumentType.DOC,
                new String[]{"321"}, "999998", 123, 456))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(eFormDocsDao);
    }

    @Test
    @DisplayName("should filter archive-backed documents from consult attachment reads")
    void shouldFilterArchiveDocsFromConsultAttachmentReads() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, 456)).thenReturn(true);
        when(consultDocsDao.findByRequestIdDocType(123, DocumentType.DOC.getType())).thenReturn(List.of(
                consultDoc(321),
                consultDoc(654)));
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThat(manager.getConsultAttachments(loggedInInfo, 123, DocumentType.DOC, 456))
                .containsExactly("654");
    }

    @Test
    @DisplayName("should filter archive-backed documents from eForm attachment reads")
    void shouldFilterArchiveDocsFromEFormAttachmentReads() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, 456)).thenReturn(true);
        when(eFormDocsDao.findByFdidIdDocType(123, DocumentType.DOC.getType())).thenReturn(List.of(
                eFormDoc(321),
                eFormDoc(654)));
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThat(manager.getEFormAttachments(loggedInInfo, 123, DocumentType.DOC, 456))
                .containsExactly("654");
    }

    private ConsultDocs consultDoc(int documentNo) {
        ConsultDocs consultDoc = new ConsultDocs();
        consultDoc.setDocumentNo(documentNo);
        consultDoc.setDocType(DocumentType.DOC.getType());
        return consultDoc;
    }

    private EFormDocs eFormDoc(int documentNo) {
        EFormDocs eFormDoc = new EFormDocs();
        eFormDoc.setDocumentNo(documentNo);
        eFormDoc.setDocType(DocumentType.DOC.getType());
        return eFormDoc;
    }
}
