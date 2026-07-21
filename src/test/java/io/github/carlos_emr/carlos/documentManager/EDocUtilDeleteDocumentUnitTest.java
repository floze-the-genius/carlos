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

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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

@DisplayName("EDocUtil.deleteDocument")
@Tag("unit")
@Tag("documentManager")
class EDocUtilDeleteDocumentUnitTest extends CarlosUnitTestBase {

    private DocumentDao documentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;

    @BeforeEach
    void setUp() throws Exception {
        clearCachedDocumentDao();
        documentDao = mock(DocumentDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        registerMock(DocumentDao.class, documentDao);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);
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

    private Document activeDocument() {
        Document document = new Document();
        document.setDocumentNo(321);
        document.setStatus(Document.STATUS_ACTIVE);
        return document;
    }

    private void clearCachedDocumentDao() throws Exception {
        Field documentDaoField = EDocUtil.class.getDeclaredField("documentDao");
        documentDaoField.setAccessible(true);
        documentDaoField.set(null, null);
    }
}
