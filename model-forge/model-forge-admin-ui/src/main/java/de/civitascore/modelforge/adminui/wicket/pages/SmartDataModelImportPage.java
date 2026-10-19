package de.civitascore.modelforge.adminui.wicket.pages;

import de.civitascore.modelforge.adminui.wicket.BasePage;
import de.civitascore.modelforge.contract.ImportSmartDataModelCommand;
import de.civitascore.modelforge.facade.ModelForge;
import java.io.Serializable;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Imports a JSON schema from the public Smart Data Models catalogue
 * (https://smartdatamodels.org) by its {@code (subject, dataModel)} pair, e.g.
 * subject {@code Weather}, dataModel {@code WeatherObserved}.
 */
public class SmartDataModelImportPage extends BasePage {

    @SpringBean
    private ModelForge modelForge;

    public SmartDataModelImportPage() {
        State state = new State();
        state.setSubject("Weather");
        state.setDataModel("WeatherObserved");

        Form<Void> form = new Form<>("importForm") {
            @Override
            protected void onSubmit() {
                try {
                    var result = modelForge.importFromSmartDataModels(
                        new ImportSmartDataModelCommand(state.getSubject(), state.getDataModel()));
                    setResponsePage(ArtifactViewPage.class,
                        new PageParameters().add("urn", result.rootArtifactId().value()));
                } catch (RuntimeException e) {
                    error("Import failed: " + e.getMessage());
                }
            }
        };
        form.add(new TextField<>("subject", new PropertyModel<>(state, "subject")));
        form.add(new TextField<>("dataModel", new PropertyModel<>(state, "dataModel")));
        add(form);
    }

    /** Mutable holder for the form's bound fields. */
    public static class State implements Serializable {
        private String subject;
        private String dataModel;

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getDataModel() {
            return dataModel;
        }

        public void setDataModel(String dataModel) {
            this.dataModel = dataModel;
        }
    }
}
