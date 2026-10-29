package com.example.backend.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.backend.dto.ErrorCampoDTO;
import com.example.backend.dto.ErrorResponseDTO;

/**
 * Traduce las excepciones a respuestas HTTP con un cuerpo uniforme
 * ({@link ErrorResponseDTO}) en toda la API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<ErrorResponseDTO> manejarDuplicado(RecursoDuplicadoException ex) {
        return responder(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponseDTO> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({CredencialesInvalidasException.class, SesionNoAutenticadaException.class})
    public ResponseEntity<ErrorResponseDTO> manejarNoAutenticado(RuntimeException ex) {
        return responder(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(PermisoDenegadoException.class)
    public ResponseEntity<ErrorResponseDTO> manejarPermisoDenegado(PermisoDenegadoException ex) {
        return responder(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<ErrorResponseDTO> manejarOperacionInvalida(OperacionInvalidaException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> manejarValidacion(MethodArgumentNotValidException ex) {
        List<ErrorCampoDTO> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::aErrorCampo)
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("Hay datos inválidos en la solicitud", errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no tiene un formato válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> manejarTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return responder(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' no tiene un valor válido");
    }

    // Restricciones únicas o llaves foráneas de la base de datos que no
    // alcanzaron a validarse en la capa de servicio.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> manejarIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT,
                "La operación entra en conflicto con datos existentes (por ejemplo, un nombre repetido)");
    }

    // Excepciones propias de Spring MVC (ruta inexistente, método no permitido...)
    // conservan su código HTTP; cualquier otra se registra y responde 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> manejarGeneral(Exception ex) {
        if (ex instanceof ErrorResponse errorSpring) {
            HttpStatusCode estado = errorSpring.getStatusCode();
            String detalle = errorSpring.getBody().getDetail();
            return ResponseEntity
                    .status(estado)
                    .body(ErrorResponseDTO.de(detalle != null ? detalle : "La solicitud no pudo procesarse"));
        }

        log.error("Error no controlado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Intente nuevamente.");
    }

    private ErrorCampoDTO aErrorCampo(FieldError error) {
        return new ErrorCampoDTO(error.getField(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponseDTO> responder(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(ErrorResponseDTO.de(mensaje));
    }
}
