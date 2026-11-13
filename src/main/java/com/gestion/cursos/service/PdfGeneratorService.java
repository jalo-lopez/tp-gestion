package com.gestion.cursos.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Servicio para generación de documentos oficiales en formato PDF:
 * 1. Propuesta y Presupuesto del Curso (elaborada/revisada por el Jefe Administrativo).
 * 2. Informe Final de Resultados (elaborado por el Jefe Docente).
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Genera el documento PDF de la Propuesta Académica y Presupuestaria.
     */
    public byte[] generarPdfPropuesta(String cursoId, String nombreCurso, Map<String, Object> variables) {
        log.info("Generando PDF de propuesta para curso [{}]", cursoId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, out);
            document.open();

            // Título principal
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(26, 54, 93));
            Paragraph title = new Paragraph("PROPUESTA DE CAPACITACIÓN INSTITUCIONAL", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Subtítulo
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.GRAY);
            Paragraph subtitle = new Paragraph("Documento Oficial de Acuerdo Formativo - Sistema BPM", subTitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(25);
            document.add(subtitle);

            // Tabla de Datos
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(20);
            table.setWidths(new float[]{35f, 65f});

            agregarFila(table, "Código de Curso:", cursoId);
            agregarFila(table, "Nombre de la Capacitación:", nombreCurso);
            agregarFila(table, "Categoría / Área:", String.valueOf(variables.getOrDefault("categoria", "Especialización Profesional")));
            agregarFila(table, "Empresa Solicitante:", String.valueOf(variables.getOrDefault("emailSolicitante", "Empresa Cliente")));
            agregarFila(table, "Cupo de Participantes:", String.valueOf(variables.getOrDefault("cupoMaximo", "30")));
            agregarFila(table, "Fechas Propuestas:", String.valueOf(variables.getOrDefault("fechasPosibles", "A convenir")));
            agregarFila(table, "Presupuesto Estimado:", String.valueOf(variables.getOrDefault("presupuesto", "$ 850.000 ARS")));
            agregarFila(table, "Programa / Temario:", String.valueOf(variables.getOrDefault("programa", "Módulos teóricos y laboratorios prácticos")));
            agregarFila(table, "Fecha de Emisión:", LocalDateTime.now().format(FORMATTER));

            document.add(table);

            // Observaciones o notas
            Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.DARK_GRAY);
            Paragraph note = new Paragraph("Nota: La presente propuesta tiene una validez sujeta a confirmación por parte de la empresa solicitante. Emitido por el Área de Administración.", noteFont);
            note.setSpacingBefore(20);
            document.add(note);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error al generar PDF de propuesta: {}", e.getMessage(), e);
            throw new RuntimeException("Error en la creación del PDF de propuesta", e);
        }
    }

    /**
     * Genera el documento PDF del Informe Final de Resultados.
     */
    public byte[] generarPdfInformeResultados(String cursoId, String nombreCurso, Map<String, Object> variables) {
        log.info("Generando PDF de informe de resultados para curso [{}]", cursoId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, out);
            document.open();

            // Encabezado
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(40, 116, 166));
            Paragraph title = new Paragraph("INFORME FINAL DE RESULTADOS DEL CURSO", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.GRAY);
            Paragraph subtitle = new Paragraph("Evaluación de Dictado y Conclusión Académica", subTitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(25);
            document.add(subtitle);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(20);
            table.setWidths(new float[]{35f, 65f});

            agregarFila(table, "Código de Curso:", cursoId);
            agregarFila(table, "Capacitación Dictada:", nombreCurso);
            agregarFila(table, "Docente a Cargo:", String.valueOf(variables.getOrDefault("docenteAsignado", "docente_carlos")));
            agregarFila(table, "Fecha de Finalización:", LocalDateTime.now().format(FORMATTER));
            agregarFila(table, "Conclusión de Dictado:", String.valueOf(variables.getOrDefault("dictadoCompletado", "Completado satisfactoriamente")));
            agregarFila(table, "Síntesis del Informe:", String.valueOf(variables.getOrDefault("informeResultados", "El curso se desarrolló con alto nivel de participación y cumplimiento del programa.")));

            document.add(table);

            Font noteFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.DARK_GRAY);
            Paragraph note = new Paragraph("Aprobado por el Jefe del Equipo Docente y archivado por la Administración.", noteFont);
            note.setSpacingBefore(20);
            document.add(note);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error al generar PDF de informe: {}", e.getMessage(), e);
            throw new RuntimeException("Error en la creación del PDF de informe", e);
        }
    }

    private void agregarFila(PdfPTable table, String campo, String valor) {
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);

        PdfPCell cellCampo = new PdfPCell(new Phrase(campo, boldFont));
        cellCampo.setBackgroundColor(new Color(245, 247, 250));
        cellCampo.setPadding(8);

        PdfPCell cellValor = new PdfPCell(new Phrase(valor != null ? valor : "N/D", regularFont));
        cellValor.setPadding(8);

        table.addCell(cellCampo);
        table.addCell(cellValor);
    }
}
