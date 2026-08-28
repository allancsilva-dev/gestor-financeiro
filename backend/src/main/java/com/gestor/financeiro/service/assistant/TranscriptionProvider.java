package com.gestor.financeiro.service.assistant;

import java.nio.file.Path;

public interface TranscriptionProvider {
    String transcribe(Path audio);
    String provider();
    String model();
}
