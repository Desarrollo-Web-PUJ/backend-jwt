package com.example.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.EmpresaRegistroRequestDTO;
import com.example.backend.dto.EmpresaResponseDTO;
import com.example.backend.service.EmpresaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/empresas")
@Tag(name = "Empresas", description = "HU-01: registro de empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @PostMapping("/registro")
    @Operation(summary = "Registrar una empresa junto con su usuario administrador inicial")
    public ResponseEntity<EmpresaResponseDTO> registrarEmpresa(@Valid @RequestBody EmpresaRegistroRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaService.registrarEmpresa(request));
    }
}
