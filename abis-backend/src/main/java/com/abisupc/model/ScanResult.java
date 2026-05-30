package com.abisupc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Representa el resultado del OCR sobre una cedula de ciudadania colombiana.
 *
 * <p>Lo produce el microservicio {@code abis-ocr} (FastAPI :8002) y lo consume
 * {@code OcrController}. {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * permite que el modelo sea tolerante a campos adicionales que el microservicio
 * pueda agregar en versiones futuras sin romper la deserializacion.
 *
 * <p>{@code overallConfidence} y {@code classificationConfidence} son scores
 * entre 0 y 1 que indican la confianza del motor OCR. {@code fieldConfidence}
 * desglosa la confianza por campo individual (nombre, apellido, fecha, etc.).
 * Si {@code status} es distinto de {@code "success"}, {@code errors} contiene
 * los mensajes de fallo del pipeline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanResult {

    @JsonProperty("document_type")
    private String documentType;

    private String status;

    @JsonProperty("overall_confidence")
    private Double overallConfidence;

    @JsonProperty("classification_confidence")
    private Double classificationConfidence;

    private List<String> errors;

    private String numero;
    private String nombres;
    private String apellidos;

    @JsonProperty("primer_nombre")
    private String primerNombre;

    @JsonProperty("segundo_nombre")
    private String segundoNombre;

    @JsonProperty("primer_apellido")
    private String primerApellido;

    @JsonProperty("segundo_apellido")
    private String segundoApellido;

    @JsonProperty("fecha_nacimiento")
    private String fechaNacimiento;

    @JsonProperty("fecha_expiracion")
    private String fechaExpiracion;

    private String sexo;

    @JsonProperty("lugar_nacimiento")
    private String lugarNacimiento;

    @JsonProperty("field_confidence")
    private Map<String, Double> fieldConfidence;

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String v) { this.documentType = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Double getOverallConfidence() { return overallConfidence; }
    public void setOverallConfidence(Double v) { this.overallConfidence = v; }

    public Double getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(Double v) { this.classificationConfidence = v; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> v) { this.errors = v; }

    public String getNumero() { return numero; }
    public void setNumero(String v) { this.numero = v; }

    public String getNombres() { return nombres; }
    public void setNombres(String v) { this.nombres = v; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String v) { this.apellidos = v; }

    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String v) { this.primerNombre = v; }

    public String getSegundoNombre() { return segundoNombre; }
    public void setSegundoNombre(String v) { this.segundoNombre = v; }

    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String v) { this.primerApellido = v; }

    public String getSegundoApellido() { return segundoApellido; }
    public void setSegundoApellido(String v) { this.segundoApellido = v; }

    public String getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(String v) { this.fechaNacimiento = v; }

    public String getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(String v) { this.fechaExpiracion = v; }

    public String getSexo() { return sexo; }
    public void setSexo(String v) { this.sexo = v; }

    public String getLugarNacimiento() { return lugarNacimiento; }
    public void setLugarNacimiento(String v) { this.lugarNacimiento = v; }

    public Map<String, Double> getFieldConfidence() { return fieldConfidence; }
    public void setFieldConfidence(Map<String, Double> v) { this.fieldConfidence = v; }
}
