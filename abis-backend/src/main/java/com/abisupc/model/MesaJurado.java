package com.abisupc.model;

import java.time.LocalDateTime;

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