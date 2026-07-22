package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.dto.DocumentListItemDTO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("DocumentManagerImpl filename validation")
@Tag("unit")
@Tag("security")
class DocumentManagerImplFilenameValidationTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";

    private SecurityInfoManager securityInfoManager;
    private DocumentDao documentDao;
    private CtlDocumentDao ctlDocumentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private QueueDocumentLinkDao queueDocumentLinkDao;
    private LoggedInInfo loggedInInfo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        queueDocumentLinkDao = mock(QueueDocumentLinkDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("w"), eq("")))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("x"), eq("")))
                .thenReturn(true);
        doAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setDocumentNo(321);
            return null;
        }).when(documentDao).persist(any(Document.class));
    }

    @Test
    @DisplayName("should keep timestamp prefix after normalizing path-shaped document filename")
    void shouldKeepTimestampPrefix_afterNormalizingPathShapedDocumentFilename() throws Exception {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());

            DocumentManagerImpl manager = newDocumentManager();
            Document document = new Document();
            document.setDocfilename("nested/path/my report.txt");

            Document result = manager.createDocument(loggedInInfo, document, null, null,
                    "document body".getBytes(StandardCharsets.UTF_8));

            assertThat(result.getDocfilename()).matches("\\d{14}_my_report\\.txt");
            assertThat(Files.readString(tempDir.resolve(result.getDocfilename()))).isEqualTo("document body");
            assertThat(Files.exists(tempDir.resolve("my_report.txt"))).isFalse();
        }
    }

    @Test
    @DisplayName("should reject generated filenames that belong to outbound archive eDocs")
    void shouldRejectCreateDocument_whenGeneratedFilenameBelongsToOutboundArchive() throws Exception {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());
            when(outboundEmailArchiveDao.existsByFileName(anyString())).thenReturn(true);

            DocumentManagerImpl manager = newDocumentManager();
            Document document = new Document();
            document.setDocfilename("archive.pdf");

            assertThatThrownBy(() -> manager.createDocument(loggedInInfo, document, null, null,
                    "document body".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("outbound email archive workflow");
            try (Stream<Path> files = Files.list(tempDir)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("should reject normal rendering for outbound archive backing eDocs")
    void shouldRejectRenderDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_newCasemgmt.documents"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.renderDocument(loggedInInfo, "321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject generic document reads for outbound archive backing eDocs")
    void shouldRejectGetDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(321);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.getDocument(loggedInInfo, 321))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should filter outbound archive eDocs from generic document sync lists")
    void shouldFilterOutboundArchiveDocuments_whenListingUpdatedDocuments() {
        DocumentManagerImpl manager = newDocumentManager();
        Document archiveDocument = document(321);
        Document ordinaryDocument = document(654);
        Date since = new Date(0L);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
        when(documentDao.findByUpdateDate(since, 10)).thenReturn(List.of(archiveDocument, ordinaryDocument));
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        List<Document> documents = manager.getDocumentsUpdateAfterDate(loggedInInfo, since, 10);

        assertThat(documents).containsExactly(ordinaryDocument);
    }

    @Test
    @DisplayName("should filter outbound archive eDocs from generic document DTO lists")
    void shouldFilterOutboundArchiveDocuments_whenListingDocumentDtos() {
        DocumentManagerImpl manager = newDocumentManager();
        DocumentListItemDTO archiveDocument = documentDto(321);
        DocumentListItemDTO ordinaryDocument = documentDto(654);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(true);
        when(documentDao.findDocumentDTOsByDemographicNo(123)).thenReturn(List.of(archiveDocument, ordinaryDocument));
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        List<DocumentListItemDTO> documents = manager.getDocumentDTOs(loggedInInfo, 123);

        assertThat(documents)
                .extracting(DocumentListItemDTO::getDocumentNo)
                .containsExactly(654);
    }

    @Test
    @DisplayName("should reject moving outbound archive backing eDocs")
    void shouldRejectMoveDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(321);
        document.setDocfilename("ordinary.pdf");
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, document, tempDir.toString(), tempDir.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject moving documents over outbound archive filenames")
    void shouldRejectMoveDocument_whenFilenameBelongsToOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(654);
        document.setDocfilename("archive.pdf");
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, document, tempDir.toString(), tempDir.toString()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject saving outbound archive backing eDocs through generic updates")
    void shouldRejectSaveDocument_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(321);
        document.setDocfilename("ordinary.pdf");
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.saveDocument(loggedInInfo, document, ctlDocument(123)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject saving documents over outbound archive filenames")
    void shouldRejectSaveDocument_whenFilenameBelongsToOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(654);
        document.setDocfilename("archive.pdf");
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> manager.saveDocument(loggedInInfo, document, ctlDocument(123)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject writing documents over outbound archive filenames")
    void shouldRejectAddDocument_whenFilenameBelongsToOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        Document document = document(null);
        document.setDocfilename("archive.pdf");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_newCasemgmt.documents"),
                eq(SecurityInfoManager.WRITE), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_newCasemgmt.documents"),
                eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> manager.addDocument(loggedInInfo, document, ctlDocument(123)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    @Test
    @DisplayName("should reject queueing outbound archive backing eDocs")
    void shouldRejectAddDocumentToQueue_whenDocumentBacksOutboundArchive() {
        DocumentManagerImpl manager = newDocumentManager();
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> manager.addDocumentToQueue(loggedInInfo, 321, 7))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outbound email archive workflow");
    }

    private Document document(Integer documentNo) {
        Document document = new Document();
        document.setDocumentNo(documentNo);
        document.setStatus(Document.STATUS_ACTIVE);
        return document;
    }

    private DocumentListItemDTO documentDto(Integer documentNo) {
        DocumentListItemDTO document = new DocumentListItemDTO();
        document.setDocumentNo(documentNo);
        return document;
    }

    private CtlDocument ctlDocument(Integer demographicNo) {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK("demographic", demographicNo, null));
        return ctlDocument;
    }

    private DocumentManagerImpl newDocumentManager() {
        DocumentManagerImpl manager = new DocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "documentDao", documentDao);
        injectDependency(manager, "ctlDocumentDao", ctlDocumentDao);
        injectDependency(manager, "outboundEmailArchiveDao", outboundEmailArchiveDao);
        injectDependency(manager, "patientLabRoutingDao", patientLabRoutingDao);
        injectDependency(manager, "providerLabRoutingDao", providerLabRoutingDao);
        injectDependency(manager, "queueDocumentLinkDAO", queueDocumentLinkDao);
        return manager;
    }
}
