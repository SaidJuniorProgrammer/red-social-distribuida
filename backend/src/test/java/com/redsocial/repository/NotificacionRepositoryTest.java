package com.redsocial.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacionRepositoryTest {

    NotificacionRepository repository;
    Driver mockDriver;
    Session mockSession;
    TransactionContext mockTx;
    Result mockResult;

    @BeforeEach
    void setUp() {
        repository = new NotificacionRepository();
        mockDriver = Mockito.mock(Driver.class);
        mockSession = Mockito.mock(Session.class);
        mockTx = Mockito.mock(TransactionContext.class);
        mockResult = Mockito.mock(Result.class);
        
        repository.driver = mockDriver;

        Mockito.when(mockDriver.session()).thenReturn(mockSession);
        
        // Interceptar transacciones
        Mockito.when(mockSession.executeWrite(Mockito.any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(mockTx);
        });
        Mockito.when(mockSession.executeRead(Mockito.any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(mockTx);
        });

        // Configurar el resultado por defecto
        Mockito.when(mockTx.run(Mockito.anyString(), Mockito.any(Value.class))).thenReturn(mockResult);
    }

    @Test
    void testGuardarNotificacion() {
        assertDoesNotThrow(() -> repository.guardarNotificacion("id", "LIKE", "actor", "dest", "msg", "url", "fecha"));
    }

    @Test
    void testListarNotificacionesVacio() {
        Mockito.when(mockResult.hasNext()).thenReturn(false);
        assertTrue(repository.listarNotificaciones("dest").isEmpty());
    }

    @Test
    void testListarNotificacionesConDatos() {
        Mockito.when(mockResult.hasNext()).thenReturn(true, false);
        Record mockRecord = Mockito.mock(Record.class);
        Mockito.when(mockResult.next()).thenReturn(mockRecord);
        
        Value mockValue = Mockito.mock(Value.class);
        Mockito.when(mockRecord.get(Mockito.anyString())).thenReturn(mockValue);
        Mockito.when(mockValue.asString()).thenReturn("texto_prueba");
        Mockito.when(mockValue.asBoolean(false)).thenReturn(true);

        assertFalse(repository.listarNotificaciones("dest").isEmpty());
    }

    @Test
    void testMarcarComoLeida() {
        Mockito.when(mockResult.hasNext()).thenReturn(true);
        assertTrue(repository.marcarComoLeida("id", "dest"));
    }

    @Test
    void testMarcarTodasComoLeidas() {
        Record mockRecord = Mockito.mock(Record.class);
        Value mockValue = Mockito.mock(Value.class);
        Mockito.when(mockResult.hasNext()).thenReturn(true);
        Mockito.when(mockResult.next()).thenReturn(mockRecord);
        Mockito.when(mockRecord.get("actualizadas")).thenReturn(mockValue);
        Mockito.when(mockValue.asLong(0L)).thenReturn(5L);
        
        assertEquals(5L, repository.marcarTodasComoLeidas("dest"));
    }

    @Test
    void testContarNoLeidas() {
        Record mockRecord = Mockito.mock(Record.class);
        Value mockValue = Mockito.mock(Value.class);
        Mockito.when(mockResult.hasNext()).thenReturn(true);
        Mockito.when(mockResult.next()).thenReturn(mockRecord);
        Mockito.when(mockRecord.get("no_leidas")).thenReturn(mockValue);
        Mockito.when(mockValue.asLong(0L)).thenReturn(3L);
        
        assertEquals(3L, repository.contarNoLeidas("dest"));
    }
}