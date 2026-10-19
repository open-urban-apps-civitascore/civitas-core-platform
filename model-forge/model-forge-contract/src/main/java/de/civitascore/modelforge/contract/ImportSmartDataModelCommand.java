package de.civitascore.modelforge.contract;

/**
 * Imports a JSON schema from the public <a href="https://smartdatamodels.org">Smart Data
 * Models</a> catalogue, identified by its {@code (subject, dataModel)} pair (e.g. subject
 * {@code "Weather"}, dataModel {@code "WeatherObserved"}).
 */
public record ImportSmartDataModelCommand(String subject, String dataModel) {
}
