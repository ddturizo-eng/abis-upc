package com.abisupc.model;

import java.time.LocalDateTime;

/**
 * Representa una mesa de jurados en un puesto de votacion.
 *
 * <p>Una mesa esta activa mientras {@code horaSalida} sea {@code null}.
 * Cuando la jornada termina, {@code MesaJuradoRepository.update()} registra
 * la hora de cierre. {@code idPuesto} referencia el puesto fisico donde
 * opera la mesa. Tabla Oracle: {@code MESA_JURADOS}.
 */
public class MesaJurado extends Entity {

    private LocalDateTime horaIngreso;
    private LocalDateTime horaSalida;
    private String cargo;
    private Long idPuesto;

    public LocalDateTime getHoraIngreso() {
        return horaIngreso;
    }
    public void setHoraIngreso(LocalDateTime horaIngreso) {
        this.horaIngreso = horaIngreso;
    }

    public LocalDateTime getHoraSalida() {
        return horaSalida;
    }
    public void setHoraSalida(LocalDateTime horaSalida) {
        this.horaSalida = horaSalida;
    }

    public String getCargo() {
        return cargo;
    }
    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public Long getIdPuesto() {
        return idPuesto;
    }
    public void setIdPuesto(Long idPuesto) {
        this.idPuesto = idPuesto;
    }
}