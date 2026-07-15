package de.civitascore.modelforge.adminui.wicket.pages;

import de.civitascore.modelforge.adminui.seed.SeedBundle;
import de.civitascore.modelforge.adminui.seed.SeedImporter;
import de.civitascore.modelforge.adminui.seed.SeedImporter.SeedResult;
import de.civitascore.modelforge.adminui.wicket.BasePage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Seed models page: import whole bundled example model sets (discovered on the classpath under
 * {@code seed/<id>/}) with one click, or upload an ad-hoc bundle of {@code *.schema.json} files.
 * Both paths run through {@link SeedImporter} and are idempotent.
 *
 * <p>After an import the page redirects to a fresh instance so the sidebar (built in
 * {@link BasePage}) reflects the newly imported artifacts; the result summary is carried across via
 * session-scoped feedback.
 */
public class SeedPage extends BasePage {

    @SpringBean
    private SeedImporter seedImporter;

    public SeedPage() {
        // Bundled seed sets, each with a one-click import.
        List<SeedBundle> bundles = seedImporter.discoverBundles();
        add(new ListView<SeedBundle>("bundles", bundles) {
            @Override
            protected void populateItem(ListItem<SeedBundle> item) {
                SeedBundle bundle = item.getModelObject();
                item.add(new Label("bundleTitle", bundle.title()));
                item.add(new Label("bundleSub", bundle.id() + " · " + bundle.count() + " elements"));
                item.add(new Link<Void>("importLink") {
                    @Override
                    public void onClick() {
                        reportAndReload(seedImporter.importClasspath(bundle.locationPattern()));
                    }
                });
            }
        });
        add(new Label("noBundles", "No bundled seed sets found on the classpath.")
            .setVisible(bundles.isEmpty()));

        // Ad-hoc multi-file upload.
        FileUploadField fileUpload = new FileUploadField("files");
        Form<Void> uploadForm = new Form<>("uploadForm") {
            @Override
            protected void onSubmit() {
                List<FileUpload> uploads = fileUpload.getFileUploads();
                if (uploads == null || uploads.isEmpty()) {
                    error("No files selected.");
                    return;
                }
                Map<String, byte[]> files = new LinkedHashMap<>();
                for (FileUpload upload : uploads) {
                    files.put(upload.getClientFileName(), upload.getBytes());
                }
                reportAndReload(seedImporter.importRaw(files));
            }
        };
        uploadForm.setMultiPart(true);
        uploadForm.add(fileUpload);
        add(uploadForm);
    }

    private void reportAndReload(SeedResult result) {
        getSession().success(result.imported() + " imported, " + result.present()
            + " already present, " + result.failed() + " failed.");
        for (String message : result.messages()) {
            getSession().warn(message);
        }
        setResponsePage(new SeedPage());
    }
}
