/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.active.learning.sidebar;

import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.visibleWhen;
import static de.tudarmstadt.ukp.inception.active.learning.sidebar.ActiveLearningUserStateMetaData.CURRENT_AL_USER_STATE;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordType.ACCEPTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordType.CORRECTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordType.REJECTED;
import static de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordType.SKIPPED;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wicketstuff.event.annotation.OnEvent;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.JCasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupport;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.editor.FeatureEditor;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.FeatureState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.event.RenderAnnotationsEvent;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VAnnotationMarker;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VMarker;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VTextMarker;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.support.dialog.ConfirmationDialog;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxButton;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaChoiceRenderer;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaModel;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaModelAdapter;
import de.tudarmstadt.ukp.clarin.webanno.support.spring.ApplicationEventPublisherHolder;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebar_ImplBase;
import de.tudarmstadt.ukp.inception.active.learning.ActiveLearningService;
import de.tudarmstadt.ukp.inception.active.learning.ActiveLearningServiceImpl;
import de.tudarmstadt.ukp.inception.active.learning.ActiveLearningServiceImpl.ActiveLearningUserState;
import de.tudarmstadt.ukp.inception.active.learning.event.ActiveLearningRecommendationEvent;
import de.tudarmstadt.ukp.inception.active.learning.event.ActiveLearningSessionCompletedEvent;
import de.tudarmstadt.ukp.inception.active.learning.event.ActiveLearningSessionStartedEvent;
import de.tudarmstadt.ukp.inception.active.learning.event.ActiveLearningSuggestionOfferedEvent;
import de.tudarmstadt.ukp.inception.active.learning.strategy.ActiveLearningStrategy;
import de.tudarmstadt.ukp.inception.active.learning.strategy.UncertaintySamplingStrategy;
import de.tudarmstadt.ukp.inception.recommendation.RecommendationEditorExtension;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecord;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordChangeLocation;
import de.tudarmstadt.ukp.inception.recommendation.api.model.LearningRecordType;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Predictions;
import de.tudarmstadt.ukp.inception.recommendation.api.model.SuggestionGroup.Delta;
import de.tudarmstadt.ukp.inception.recommendation.event.AjaxPredictionsSwitchedEvent;
import de.tudarmstadt.ukp.inception.recommendation.event.AjaxRecommendationAcceptedEvent;
import de.tudarmstadt.ukp.inception.recommendation.event.AjaxRecommendationRejectedEvent;

public class ActiveLearningSidebar
    extends AnnotationSidebar_ImplBase
{
    private static final String CID_EDITOR = "editor";

    private static final long serialVersionUID = -5312616540773904224L;

    private static final Logger LOG = LoggerFactory.getLogger(ActiveLearningSidebar.class);

    // Wicket component IDs used in the HTML file
    private static final String CID_MAIN_CONTAINER = "mainContainer";
    private static final String CID_HISTORY_LISTVIEW = "historyListview";
    private static final String CID_LEARNING_HISTORY_FORM = "learningHistoryForm";
    private static final String CID_REJECT_BUTTON = "rejectButton";
    private static final String CID_SKIP_BUTTON = "skipButton";
    private static final String CID_ANNOTATE_BUTTON = "annotateButton";
    private static final String CID_RECOMMENDATION_COVERED_TEXT_LINK = "recommendationCoveredTextLink";
    private static final String CID_RECOMMENDED_DIFFERENCE = "recommendedDifference";
    private static final String CID_RECOMMENDED_CONFIDENCE = "recommendedConfidence";
    private static final String CID_RECOMMENDED_PREDITION = "recommendedPredition";
    private static final String CID_RECOMMENDATION_FORM = "recommendationForm";
    private static final String CID_LEARN_SKIPPED_ONES = "learnSkippedOnes";
    private static final String CID_ONLY_SKIPPED_RECOMMENDATION_LABEL = "onlySkippedRecommendationLabel";
    private static final String CID_LEARN_FROM_SKIPPED_RECOMMENDATION_FORM = "learnFromSkippedRecommendationForm";
    private static final String CID_NO_RECOMMENDATION_LABEL = "noRecommendationLabel";
    private static final String CID_LAYER_SELECTION_BUTTON = "layerSelectionButton";
    private static final String CID_SELECT_LAYER = "selectLayer";
    private static final String CID_SESSION_CONTROL_FORM = "sessionControlForm";
    private static final String CID_REMOVE_RECORD = "removeRecord";
    private static final String CID_USER_ACTION = "userAction";
    private static final String CID_RECOMMENDED_ANNOTATION = "recommendedAnnotation";
    private static final String CID_JUMP_TO_ANNOTATION = "jumpToAnnotation";
    private static final String CID_NO_RECOMMENDERS = "noRecommenders";
    private static final String CID_CONFIRMATION_DIALOG = "confirmationDialog";

    private static final String ANNOTATION_MARKER = "VAnnotationMarker";
    private static final String TEXT_MARKER = "VTextMarker";

    private @SpringBean ActiveLearningService activeLearningService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean RecommendationService recommendationService;
    private @SpringBean LearningRecordService learningRecordService;
    private @SpringBean DocumentService documentService;
    private @SpringBean ApplicationEventPublisherHolder applicationEventPublisherHolder;
    private @SpringBean UserDao userDao;
    private @SpringBean FeatureSupportRegistry featureSupportRegistry;

    private IModel<List<LearningRecord>> learningRecords;
    private CompoundPropertyModel<ActiveLearningServiceImpl.ActiveLearningUserState> alStateModel;

    private final WebMarkupContainer mainContainer;

    private AnnotationPage annotationPage;
    private String vMarkerType = "";
    private VID highlightVID;
    private LearningRecord selectedRecord;
    private ConfirmationDialog confirmationDialog;
    private FeatureEditor editor;
    private Form<Void> recommendationForm;

    public ActiveLearningSidebar(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, JCasProvider aJCasProvider,
            AnnotationPage aAnnotationPage)
    {
        super(aId, aModel, aActionHandler, aJCasProvider, aAnnotationPage);

        annotationPage = aAnnotationPage;

        // Instead of maintaining the AL state in the sidebar, we maintain it in the page because
        // that way we persists even if we switch to another sidebar tab
        alStateModel = new CompoundPropertyModel<>(LambdaModelAdapter.of(
            () -> aAnnotationPage.getMetaData(CURRENT_AL_USER_STATE),
            state -> aAnnotationPage.setMetaData(CURRENT_AL_USER_STATE, state)));

        // Set up the AL state in the page if it is not already there
        if (aAnnotationPage.getMetaData(CURRENT_AL_USER_STATE) == null) {
            ActiveLearningServiceImpl.ActiveLearningUserState userState = 
                    new ActiveLearningServiceImpl.ActiveLearningUserState();
            aAnnotationPage.setMetaData(CURRENT_AL_USER_STATE, userState);
        }

        mainContainer = new WebMarkupContainer(CID_MAIN_CONTAINER);
        mainContainer.setOutputMarkupId(true);
        mainContainer.add(createNoRecommendersMessage());
        mainContainer.add(createSessionControlForm());
        mainContainer.add(createNoRecommendationLabel());
        mainContainer.add(createLearnFromSkippedRecommendationForm());
        mainContainer.add(createRecommendationOperationForm());
        mainContainer.add(createLearningHistory());
        add(mainContainer);
        
        confirmationDialog = new ConfirmationDialog(CID_CONFIRMATION_DIALOG);
        add(confirmationDialog);
    }

    private Label createNoRecommendersMessage()
    {
        if (!alStateModel.getObject().isSessionActive()) {
            // Use the currently selected layer from the annotation detail editor panel as the
            // default choice in the active learning mode.
            List<AnnotationLayer> layersWithRecommenders = listLayersWithRecommenders();
            if (layersWithRecommenders.contains(getModelObject().getDefaultAnnotationLayer())) {
                alStateModel.getObject()
                    .setLayer(getModelObject().getDefaultAnnotationLayer());
            }
            // If the currently selected layer has no recommenders, use the first one which has
            else if (!layersWithRecommenders.isEmpty()) {
                alStateModel.getObject().setLayer(layersWithRecommenders.get(0));
            }
            // If there are no layers with recommenders, then choose nothing and show no
            // recommenders message.
            else {
                alStateModel.getObject().setLayer(null);
                alStateModel.getObject().setDoExistRecommenders(false);
            }
        }
        Label noRecommendersMessage = new Label(CID_NO_RECOMMENDERS, "None of the layers have any "
            + "recommenders configured. Please set the recommenders first in the Project "
            + "Settings.");
        noRecommendersMessage.add(LambdaBehavior.onConfigure(component -> component.setVisible
            (!alStateModel.getObject().isDoExistRecommenders())));
        return noRecommendersMessage;
    }

    private Form<Void> createSessionControlForm()
    {
        Form<Void> form = new Form<>(CID_SESSION_CONTROL_FORM);

        DropDownChoice<AnnotationLayer> layersDropdown = new DropDownChoice<>(CID_SELECT_LAYER);
        layersDropdown.setModel(alStateModel.bind("layer"));
        layersDropdown.setChoices(LoadableDetachableModel.of(this::listLayersWithRecommenders));
        layersDropdown.setChoiceRenderer(new LambdaChoiceRenderer<>(AnnotationLayer::getUiName));
        layersDropdown.add(LambdaBehavior.onConfigure(it -> it.setEnabled(!alStateModel
            .getObject().isSessionActive())));
        layersDropdown.setOutputMarkupId(true);
        layersDropdown.setRequired(true);
        form.add(layersDropdown);
        
        LambdaAjaxButton<Void> startStopButton = new LambdaAjaxButton<>(
                CID_LAYER_SELECTION_BUTTON, this::actionStartStopSession);
        startStopButton.setModel(LoadableDetachableModel
            .of(() -> alStateModel.getObject().isSessionActive() ? "Terminate" : "Start"));
        form.add(startStopButton);
        form.add(visibleWhen(() -> alStateModel.getObject().isDoExistRecommenders()));

        return form;
    }

    private List<AnnotationLayer> listLayersWithRecommenders()
    {
        return recommendationService
                .listLayersWithEnabledRecommenders(getModelObject().getProject());
    }
    
    private void actionStartStopSession(AjaxRequestTarget target, Form<?> form)
    {
        ActiveLearningUserState alState = alStateModel.getObject();

        if (!alState.isSessionActive()) {
            actionStartSession(target, form);
        }
        else {
            actionStopSession(target, form);
        }
    }

    private void actionStartSession(AjaxRequestTarget target, Form<?> form)
    {
        // Start new session
        target.add(mainContainer);

        ActiveLearningUserState alState = alStateModel.getObject();
        AnnotatorState annotatorState = getModelObject();

        alState.setSessionActive(true);
        alState.setLearnSkippedRecommendationTime(null);

        alState.setListOfRecommendationsForEachToken(
                activeLearningService.getRecommendationFromRecommendationModel(annotatorState,
                        alStateModel.getObject().getLayer()));

        ActiveLearningStrategy alStrategy = new UncertaintySamplingStrategy(annotatorState,
                alState.getLayer());
        alState.setStrategy(alStrategy);

        alState.setCurrentDifference(
                alStrategy.generateRecommendationWithLowestDifference(
                        learningRecordService, alState.getLearnSkippedRecommendationTime(),
                        alState.getListOfRecommendationsForEachToken()));
        showAndHighlightRecommendationAndJumpToRecommendationLocation(target);

        applicationEventPublisherHolder.get().publishEvent(new ActiveLearningSessionStartedEvent(
                this, annotatorState.getProject(), annotatorState.getUser().getUsername()));
    }

    private void actionStopSession(AjaxRequestTarget target, Form<?> form)
    {
        // Stop current session
        target.add(mainContainer);

        ActiveLearningUserState alState = alStateModel.getObject();
        AnnotatorState annotatorState = getModelObject();

        alState.setSessionActive(false);

        applicationEventPublisherHolder.get().publishEvent(new ActiveLearningSessionCompletedEvent(
                this, annotatorState.getProject(), annotatorState.getUser().getUsername()));
    }
    
    private void showAndHighlightRecommendationAndJumpToRecommendationLocation(
            AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        ActiveLearningUserState alState = alStateModel.getObject();
        
        if (alState.getCurrentDifference().isPresent()) {
            alState.setHasUnseenRecommendation(true);
            AnnotationSuggestion currentRecommendation = alStateModel.getObject()
                    .getCurrentRecommendation().get();
            
            SourceDocument sourceDocument = documentService.getSourceDocument(
                    state.getProject(), currentRecommendation.getDocumentName());
            
            try {
                refreshFeatureEditor(aTarget, currentRecommendation);
                
                // jump to the document of that recommendation
                actionShowSelectedDocument(aTarget, sourceDocument,
                        currentRecommendation.getBegin());
                
                highlightCurrentRecommendation(aTarget);
            }
            catch (IOException e) {
                LOG.error("Unable to switch to document : {} ", e.getMessage(), e);
                error("Unable to switch to document : " + e.getMessage());
                aTarget.addChildren(getPage(), IFeedback.class);
            }
            
            // Send an application event that the suggestion has been rejected
            List<AnnotationSuggestion> alternativeSuggestions = recommendationService
                    .getPredictions(state.getUser(), state.getProject())
                    .getPredictionsByTokenAndFeature(currentRecommendation.getDocumentName(),
                            alState.getLayer(), currentRecommendation.getBegin(),
                            currentRecommendation.getEnd(), currentRecommendation.getFeature());

            applicationEventPublisherHolder.get()
                    .publishEvent(new ActiveLearningSuggestionOfferedEvent(this, sourceDocument,
                            currentRecommendation, state.getUser().getUsername(),
                            alState.getLayer(), currentRecommendation.getFeature(),
                            alternativeSuggestions));
        }
        else if (alState.getLearnSkippedRecommendationTime() == null) {
            alState.setHasUnseenRecommendation(false);
            boolean hasSkippedRecommendation = alStateModel.getObject()
                    .getStrategy()
                    .hasRecommendationWhichIsSkipped(learningRecordService, activeLearningService);
            alState.setHasSkippedRecommendation(hasSkippedRecommendation);
        }
        else {
            alState.setHasUnseenRecommendation(false);
            alState.setHasSkippedRecommendation(false);
        }
    }

    private void highlightCurrentRecommendation(AjaxRequestTarget aTarget)
    {
        Optional<AnnotationSuggestion> currentRecommendation = alStateModel.getObject()
            .getCurrentRecommendation();
        
        // If  there is no current AL recommendation, then there is nothing to highlight.
        if (!currentRecommendation.isPresent()) {
            return;
        }
        
        AnnotationSuggestion rec = currentRecommendation.get();
        
        highlightRecommendation(aTarget, rec.getBegin(), rec.getEnd(), rec.getCoveredText(),
                rec.getLabel());
    }

    private void highlightRecommendation(AjaxRequestTarget aTarget, int aBegin, int aEnd,
            String aText, String aRecommendation)
    {
        AnnotatorState state = ActiveLearningSidebar.this.getModelObject();
        Predictions predictions = recommendationService.getPredictions(state.getUser(),
                state.getProject());

        // If there are no predictions then there is nothing to highlight.
        if (predictions == null) {
            return;
        }
        
        Optional<AnnotationSuggestion> aoForVID = predictions.getPrediction(state.getDocument(),
                aBegin, aEnd, aRecommendation);
        if (aoForVID.isPresent()) {
            highlightVID = new VID(RecommendationEditorExtension.BEAN_NAME,
                    alStateModel.getObject().getLayer().getId(),
                    (int) aoForVID.get().getRecommenderId(), aoForVID.get().getId(), VID.NONE,
                    VID.NONE);
            vMarkerType = ANNOTATION_MARKER;
        }
        else {
            LOG.error("Recommendation [{}] as [{}] no longer exists", aText, aRecommendation);
            error(String.format("Recommendation [%s] as [%s] no longer exists",
                    aText, aRecommendation));
            aTarget.addChildren(getPage(), IFeedback.class);
        }
    }

    private Label createNoRecommendationLabel()
    {
        Label noRecommendation = new Label(CID_NO_RECOMMENDATION_LABEL,
                "There are no further suggestions.");
        noRecommendation.add(visibleWhen(() -> {
            ActiveLearningUserState alState = alStateModel.getObject();
            return alState.isSessionActive() && !alState.isHasUnseenRecommendation()
                    && !alState.isHasSkippedRecommendation();
        }));
        noRecommendation.setOutputMarkupPlaceholderTag(true);
        return noRecommendation;
    }

    private Form<?> createLearnFromSkippedRecommendationForm()
    {
        Form<?> learnFromSkippedRecommendationForm = new Form<Void>(
                CID_LEARN_FROM_SKIPPED_RECOMMENDATION_FORM);
        learnFromSkippedRecommendationForm.add(LambdaBehavior.onConfigure(component -> component
            .setVisible(alStateModel.getObject().isSessionActive() && !alStateModel.getObject
                ().isHasUnseenRecommendation() &&
                alStateModel.getObject().isHasSkippedRecommendation())));
        learnFromSkippedRecommendationForm.setOutputMarkupPlaceholderTag(true);
        learnFromSkippedRecommendationForm.add(new Label(CID_ONLY_SKIPPED_RECOMMENDATION_LABEL, "There "
            + "are only skipped suggestions. Do you want to learn these again?"));
        learnFromSkippedRecommendationForm.add(new LambdaAjaxButton<>(CID_LEARN_SKIPPED_ONES,
            this::learnSkippedRecommendations));
        return learnFromSkippedRecommendationForm;
    }


    private void learnSkippedRecommendations(AjaxRequestTarget aTarget, Form<Void> aForm)
        throws IOException
    {
        alStateModel.getObject().setLearnSkippedRecommendationTime(new Date());

        moveToNextRecommendation(aTarget);
        
        aTarget.add(mainContainer);
    }

    private Form<Void> createRecommendationOperationForm()
    {
        recommendationForm = new Form<Void>(CID_RECOMMENDATION_FORM);
        recommendationForm.add(LambdaBehavior.onConfigure(component -> component.setVisible(
            alStateModel.getObject().isSessionActive() && alStateModel.getObject()
                .isHasUnseenRecommendation())));
        recommendationForm.setOutputMarkupPlaceholderTag(true);

        recommendationForm.add(createRecommendationCoveredTextLink());
        recommendationForm.add(new Label(CID_RECOMMENDED_PREDITION,
                LoadableDetachableModel.of(() -> alStateModel.getObject()
                        .getCurrentRecommendation().map(this::formatLabel).orElse(null))));
        recommendationForm.add(new Label(CID_RECOMMENDED_CONFIDENCE, () -> 
                alStateModel.getObject().getCurrentRecommendation()
                        .map(AnnotationSuggestion::getConfidence).orElse(null)));
        recommendationForm.add(new Label(CID_RECOMMENDED_DIFFERENCE, () -> 
                alStateModel.getObject().getCurrentDifference()
                        .map(Delta::getDelta).orElse(null)));
        recommendationForm.add((alStateModel.getObject().getLayer() != null
            && alStateModel.getObject().getCurrentRecommendation().isPresent()) ?
            initializeFeatureEditor() :
            new Label(CID_EDITOR).setVisible(false));

        recommendationForm.add(new LambdaAjaxButton<>(CID_ANNOTATE_BUTTON, this::actionAnnotate));
        recommendationForm.add(new LambdaAjaxLink(CID_SKIP_BUTTON, this::actionSkip));
        recommendationForm.add(new LambdaAjaxLink(CID_REJECT_BUTTON, this::actionReject));

        return recommendationForm;
    }

    private String formatLabel(AnnotationSuggestion aCurrentRecommendation)
    {
        AnnotationFeature feat = annotationService.getFeature(aCurrentRecommendation.getFeature(),
                alStateModel.getObject().getLayer());
        FeatureSupport<?> featureSupport = featureSupportRegistry.getFeatureSupport(feat);
        String labelValue = featureSupport.renderFeatureValue(feat,
                aCurrentRecommendation.getLabel());
        return labelValue;
    }

    private LambdaAjaxLink createRecommendationCoveredTextLink()
    {
        LambdaAjaxLink link = new LambdaAjaxLink(CID_RECOMMENDATION_COVERED_TEXT_LINK,
                this::jumpToRecommendationLocationAndHighlightRecommendation);
        link.setBody(LoadableDetachableModel.of(() -> 
                alStateModel.getObject().getCurrentRecommendation()
                        .map(it -> it.getCoveredText())
                        .orElse("")));
        return link;
    }

    private void jumpToRecommendationLocationAndHighlightRecommendation(AjaxRequestTarget aTarget)
        throws IOException
    {
        Optional<AnnotationSuggestion> currentRecommendation = alStateModel.getObject()
                .getCurrentRecommendation();
        
        if (!currentRecommendation.isPresent()) {
            return;
        }
        
        AnnotationSuggestion rec = currentRecommendation.get();
        
        actionShowSelectedDocument(aTarget, documentService.getSourceDocument(
                this.getModelObject().getProject(), rec.getDocumentName()), rec.getBegin());

        highlightCurrentRecommendation(aTarget);
    }

    private Component initializeFeatureEditor()
    {
        editor = createFeatureEditor(alStateModel.getObject().getCurrentRecommendation().get());
        return editor;
    }

    private void refreshFeatureEditor(IPartialPageRequestHandler aTarget,
            AnnotationSuggestion aCurrentRecommendation)
    {
        editor = createFeatureEditor(aCurrentRecommendation);
        recommendationForm.addOrReplace(editor);
        aTarget.add(mainContainer);
    }
    
    private FeatureEditor createFeatureEditor(AnnotationSuggestion aCurrentRecommendation)
    {
        AnnotatorState state = ActiveLearningSidebar.this.getModelObject();
        ActiveLearningUserState alState = alStateModel.getObject();
        
        // Obtain the feature state which serves as a model to the editor
        AnnotationFeature feat = annotationService.getFeature(aCurrentRecommendation.getFeature(),
                alState.getLayer());
        FeatureSupport<?> featureSupport = featureSupportRegistry.getFeatureSupport(feat);
        // We get away with passing "null" here instead of the CAS because we currently 
        // have no recommenders for any feature types that actually need the CAS (i.e.
        // link feature types and the likes).
        Object wrappedFeatureValue = featureSupport.wrapFeatureValue(feat, null,
                aCurrentRecommendation.getLabel());
        FeatureState featureState = new FeatureState(feat, (Serializable) wrappedFeatureValue);
        
        // Populate the tagset moving the tags with recommended labels to the top 
        List<Tag> tagList = annotationService.listTags(feat.getTagset());
        List<Tag> reorderedTagList = new ArrayList<>();
        if (tagList.size() > 0) {
            Predictions model = recommendationService.getPredictions(state.getUser(),
                    state.getProject());
            // get all the predictions
            List<AnnotationSuggestion> allRecommendations = model.getPredictionsByTokenAndFeature(
                    aCurrentRecommendation.getDocumentName(), alState.getLayer(),
                    aCurrentRecommendation.getBegin(), aCurrentRecommendation.getEnd(),
                    aCurrentRecommendation.getFeature());
            // get all the label of the predictions (e.g. "NN")
            List<String> allRecommendationLabels = allRecommendations.stream()
                    .map(ao -> ao.getLabel())
                    .collect(Collectors.toList());
            
            LOG.trace("Reordering tagset based on active predictions: {}", allRecommendationLabels);
            
            for (Tag tag : tagList) {
                // add the tags which contain the prediction-labels to the beginning of a tagset
                if (allRecommendationLabels.contains(tag.getName())) {
                    tag.setReordered(true);
                    reorderedTagList.add(tag);
                }
            }
            
            // remove these tags containing the prediction-labels
            tagList.removeAll(reorderedTagList);
            
            // add the rest tags to the tagset after these
            reorderedTagList.addAll(tagList);
        }
        featureState.tagset = reorderedTagList;
        
        // Finally, create the editor
        FeatureEditor featureEditor = featureSupport.createEditor(CID_EDITOR, mainContainer,
                this.getActionHandler(), this.getModel(), Model.of(featureState));
        featureEditor.setOutputMarkupPlaceholderTag(true);
        featureEditor.add(visibleWhen(() -> alStateModel.getObject().getLayer() != null
                && alState.getCurrentRecommendation().isPresent()));
        return featureEditor;
    }
    
    private void writeLearningRecordInDatabaseAndEventLog(
            AnnotationSuggestion aCurrentRecommendation, LearningRecordType aUserAction)
    {
        writeLearningRecordInDatabaseAndEventLog(aCurrentRecommendation, aUserAction,
                aCurrentRecommendation.getLabel());
    }

    private void writeLearningRecordInDatabaseAndEventLog(AnnotationSuggestion aSuggestion,
            LearningRecordType aUserAction, String aAnnotationValue)
    {
        AnnotatorState state = ActiveLearningSidebar.this.getModelObject();
        ActiveLearningUserState alState = alStateModel.getObject();

        AnnotationFeature feat = annotationService.getFeature(aSuggestion.getFeature(),
                alState.getLayer());
        SourceDocument sourceDoc = documentService.getSourceDocument(state.getProject(),
                aSuggestion.getDocumentName());

        // Log the action to the learning record
        learningRecordService.logLearningRecord(sourceDoc, state.getUser().getUsername(),
                aSuggestion, aAnnotationValue, alState.getLayer(), feat, aUserAction,
                LearningRecordChangeLocation.AL_SIDEBAR);

        // Send an application event that the suggestion has been rejected
        List<AnnotationSuggestion> alternativeSuggestions = recommendationService
                .getPredictions(state.getUser(), state.getProject())
                .getPredictionsByTokenAndFeature(aSuggestion.getDocumentName(), alState.getLayer(),
                        aSuggestion.getBegin(), aSuggestion.getEnd(), aSuggestion.getFeature());

        applicationEventPublisherHolder.get()
                .publishEvent(new ActiveLearningRecommendationEvent(this, sourceDoc, aSuggestion,
                        state.getUser().getUsername(), alState.getLayer(), aSuggestion.getFeature(),
                        aUserAction, alternativeSuggestions));
    }

    /**
     * Accept a suggestion or a corrected suggestion via the sidebar. If the value in the feature
     * editor corresponds to the originally suggested label, an acceptance is logged, otherwise
     * a correction is logged.
     * 
     * <ul>
     * <li>Creates a new annotation or updates an existing one with a new feature
     * value.</li>
     * <li>Marks the suggestions as hidden (not visible).</li>
     * <li>Logs the accepting to the learning log.</li>
     * <li>Sends events to the UI and application informing other components about the action.</li>
     * </ul>
     */    
    private void actionAnnotate(AjaxRequestTarget aTarget, Form<Void> aForm)
        throws IOException, AnnotationException
    {
        AnnotatorState state = ActiveLearningSidebar.this.getModelObject();
        ActiveLearningUserState alState = alStateModel.getObject();
        
        // There is always a current recommendation when we get here because if there is none, the
        // button to accept the recommendation is not visible.
        AnnotationSuggestion rec = alState.getCurrentRecommendation().get();
        
        aTarget.add(mainContainer);
        
        // Create AnnotationFeature and FeatureSupport
        AnnotationFeature feat = annotationService.getFeature(rec.getFeature(), alState.getLayer());
        FeatureSupport featureSupport = featureSupportRegistry.getFeatureSupport(feat);

        // Load CAS in which to create the annotation. This might be different from the one that
        // is currently viewed by the user, e.g. if the user switched to another document after
        // the suggestion has been loaded into the sidebar.
        SourceDocument sourceDoc = documentService.getSourceDocument(state.getProject(),
                rec.getDocumentName());
        String username = state.getUser().getUsername();
        JCas jCas = documentService.readAnnotationCas(sourceDoc, username);

        // Upsert an annotation based on the suggestion
        String selectedValue = (String) featureSupport.unwrapFeatureValue(feat, jCas.getCas(),
                editor.getModelObject().value);
        AnnotationLayer layer = annotationService.getLayer(rec.getLayerId());
        AnnotationFeature feature = annotationService.getFeature(rec.getFeature(), layer);
        recommendationService.upsertFeature(annotationService, sourceDoc, username, jCas, layer,
                feature, selectedValue, rec.getBegin(), rec.getEnd());
        
        // Save CAS after annotation has been created
        documentService.writeAnnotationCas(jCas, sourceDoc, state.getUser(), true);
        
        // If the currently displayed document is the same one where the annotation was created,
        // then update timestamp in state to avoid concurrent modification errors
        if (Objects.equals(state.getDocument().getId(), sourceDoc.getId())) {
            Optional<Long> diskTimestamp = documentService.getAnnotationCasTimestamp(sourceDoc,
                    username);
            if (diskTimestamp.isPresent()) {
                state.setAnnotationDocumentTimestamp(diskTimestamp.get());
            }
        }

        // Log the action to the learning record
        writeLearningRecordInDatabaseAndEventLog(rec, 
                selectedValue.equals(rec.getLabel()) ? ACCEPTED : CORRECTED, selectedValue);
        
        recommendationService.getPredictions(state.getUser(), state.getProject())
                .getPredictionsByTokenAndFeature(rec.getDocumentName(),
                        alStateModel.getObject().getLayer(), rec.getBegin(), rec.getEnd(),
                        feat.getName());

        moveToNextRecommendation(aTarget);
    }

    private void actionSkip(AjaxRequestTarget aTarget)
    {
        alStateModel.getObject().getCurrentRecommendation().ifPresent(rec -> {
            aTarget.add(mainContainer);
            writeLearningRecordInDatabaseAndEventLog(rec, SKIPPED);
            moveToNextRecommendation(aTarget);
        });
    }

    private void actionReject(AjaxRequestTarget aTarget)
    {
        alStateModel.getObject().getCurrentRecommendation().ifPresent(rec -> {
            aTarget.add(mainContainer);
            writeLearningRecordInDatabaseAndEventLog(rec, REJECTED);
            moveToNextRecommendation(aTarget);
        });
    }
    
    private void moveToNextRecommendation(AjaxRequestTarget aTarget)
    {
        
        // Clear the annotation detail editor and the selection to avoid confusions.
        AnnotatorState state = ActiveLearningSidebar.this.getModelObject();
        state.getSelection().clear();

        ActiveLearningUserState alState = alStateModel.getObject();
        ActiveLearningStrategy activeLearningRecommender = alState.getStrategy();
        alState.setCurrentDifference(activeLearningRecommender.updateRecommendations(
                learningRecordService, alState.getLearnSkippedRecommendationTime()));

        aTarget.add((Component) getActionHandler());
        annotationPage.actionRefreshDocument(aTarget);
        showAndHighlightRecommendationAndJumpToRecommendationLocation(aTarget);
    }

    private Form<?> createLearningHistory()
    {
        Form<?> learningHistoryForm = new Form<Void>(CID_LEARNING_HISTORY_FORM)
        {
            private static final long serialVersionUID = -961690443085882064L;
        };
        learningHistoryForm.add(LambdaBehavior.onConfigure(component -> component
            .setVisible(alStateModel.getObject().isSessionActive())));
        learningHistoryForm.setOutputMarkupPlaceholderTag(true);
        learningHistoryForm.setOutputMarkupId(true);

        learningHistoryForm.add(createLearningHistoryListView());
        return learningHistoryForm;
    }

    private ListView<LearningRecord> createLearningHistoryListView()
    {
        ListView<LearningRecord> learningHistory = new ListView<LearningRecord>(
                CID_HISTORY_LISTVIEW)
        {
            private static final long serialVersionUID = 5594228545985423567L;

            @Override
            protected void populateItem(ListItem<LearningRecord> item)
            {
                LearningRecord rec = item.getModelObject();
                AnnotationFeature recAnnotationFeature = rec.getAnnotationFeature();
                String recFeatureValue;
                if (recAnnotationFeature != null) {
                    FeatureSupport featureSupport = featureSupportRegistry
                        .getFeatureSupport(recAnnotationFeature);
                    recFeatureValue = featureSupport
                        .renderFeatureValue(recAnnotationFeature, rec.getAnnotation());
                }
                else {
                    recFeatureValue = rec.getAnnotation();
                }
                LambdaAjaxLink textLink = new LambdaAjaxLink(CID_JUMP_TO_ANNOTATION,
                    t -> jumpAndHighlightFromLearningHistory(t, item.getModelObject()));
                textLink.setBody(LambdaModel.of(rec::getTokenText));
                item.add(textLink);

                item.add(new Label(CID_RECOMMENDED_ANNOTATION, recFeatureValue));
                item.add(new Label(CID_USER_ACTION, rec.getUserAction()));
                item.add(
                    new LambdaAjaxLink(CID_REMOVE_RECORD, t -> actionRemoveHistoryItem(t, rec)));
            }
        };
        learningRecords = LambdaModel.of(this::listLearningRecords);
        learningHistory.setModel(learningRecords);
        return learningHistory;
    }

    private void jumpAndHighlightFromLearningHistory(AjaxRequestTarget aTarget,
            LearningRecord record)
        throws IOException
    {
        actionShowSelectedDocument(aTarget, record.getSourceDocument(),
            record.getOffsetCharacterBegin());
        JCas jCas = this.getJCasProvider().get();

        if (record.getUserAction().equals(LearningRecordType.REJECTED)) {
            highlightTextAndDisplayMessage(aTarget, record);
        }
        // if the suggestion still exists, highlight that suggestion.
        else if (alStateModel.getObject().getStrategy()
                .checkRecommendationExist(activeLearningService, record)) {
            highlightRecommendation(aTarget, record.getOffsetCharacterBegin(),
                    record.getOffsetCharacterEnd(), record.getTokenText(), record.getAnnotation());
        }
        // else if that suggestion is annotated, highlight the annotation.
        else if (!isAnnotatedInCas(record, jCas)) {
            // else, highlight the text.
            highlightTextAndDisplayMessage(aTarget, record);
        }
    }

    private boolean isAnnotatedInCas(LearningRecord aRecord, JCas aJcas)
        throws IOException
    {
        Type type = CasUtil
            .getType(aJcas.getCas(), alStateModel.getObject().getLayer().getName());
        AnnotationFS annotationFS = WebAnnoCasUtil
            .selectSingleFsAt(aJcas, type, aRecord.getOffsetCharacterBegin(),
                aRecord.getOffsetCharacterEnd());
        if (annotationFS != null) {
            for (AnnotationFeature annotationFeature : annotationService
                .listAnnotationFeature(alStateModel.getObject().getLayer())) {
                String annotatedValue = WebAnnoCasUtil
                    .getFeature(annotationFS, annotationFeature.getName());
                if (aRecord.getAnnotation().equals(annotatedValue)) {
                    highlightVID = new VID(WebAnnoCasUtil.getAddr(annotationFS));
                    vMarkerType = ANNOTATION_MARKER;
                    return true;
                }
            }
        }
        return false;
    }

    private void highlightTextAndDisplayMessage(AjaxRequestTarget aTarget, LearningRecord aRecord)
    {
        selectedRecord = aRecord;
        vMarkerType = TEXT_MARKER;
        LOG.error("No annotation could be highlighted.");
        error("No annotation could be highlighted.");
        aTarget.addChildren(getPage(), IFeedback.class);
    }

    private List<LearningRecord> listLearningRecords()
    {
        AnnotatorState annotatorState = ActiveLearningSidebar.this.getModelObject();
        return learningRecordService.getRecordsByDocumentAndUserAndLayer(
                annotatorState.getDocument(), annotatorState.getUser().getUsername(),
                alStateModel.getObject().getLayer(), 50);
    }

    private void actionRemoveHistoryItem(AjaxRequestTarget aTarget, LearningRecord aRecord)
        throws IOException
    {
        aTarget.add(mainContainer);
        annotationPage.actionRefreshDocument(aTarget);
        learningRecordService.delete(aRecord);
        learningRecords.detach();
        if (aRecord.getUserAction().equals(LearningRecordType.ACCEPTED)) {
            // IMPORTANT: we must jump to the document which contains the annotation that is to
            // be deleted because deleteAnnotationByHistory will delete the annotation via the
            // methods provided by the AnnotationActionHandler and these operate ONLY on the
            // currently visible/selected document.
            actionShowSelectedDocument(aTarget, aRecord.getSourceDocument(),
                aRecord.getOffsetCharacterBegin());
            AnnotationDocument annoDoc = documentService
                .createOrGetAnnotationDocument(aRecord.getSourceDocument(),
                    userDao.get(aRecord.getUser()));
            JCas jCas = documentService.readAnnotationCas(annoDoc);
            if (isAnnotatedInCas(aRecord, jCas)) {
                confirmationDialog.setTitleModel(
                    new StringResourceModel("alSidebar.history.delete.confirmation.title", this));
                confirmationDialog.setContentModel(
                    new StringResourceModel("alSidebar.history.delete.confirmation.content", this,
                        null));
                confirmationDialog.show(aTarget);
                confirmationDialog
                    .setConfirmAction(t -> deleteAnnotationByHistory(t, aRecord));
            }
        }
    }

    private void deleteAnnotationByHistory(AjaxRequestTarget aTarget, LearningRecord aRecord)
        throws IOException, AnnotationException
    {
        JCas jCas = this.getJCasProvider().get();
        this.getModelObject().getSelection()
            .selectSpan(highlightVID, jCas, aRecord.getOffsetCharacterBegin(),
                aRecord.getOffsetCharacterEnd());
        getActionHandler().actionDelete(aTarget);
    }

//    @OnEvent
//    public void afterAnnotationUpdateEvent(AjaxAfterAnnotationUpdateEvent aEvent)
//    {
//        AnnotatorState annotatorState = getModelObject();
//        AnnotatorState eventState = aEvent.getAnnotatorState();
//
//        // Check active learning is active and same user and same document and same layer
//        if (
//                sessionActive &&
//                currentRecommendation != null &&
//                eventState.getUser().equals(annotatorState.getUser()) &&
//                eventState.getDocument().equals(annotatorState.getDocument()) &&
//                annotatorState.getSelectedAnnotationLayer().equals(selectedLayer.getObject())
//        ) {
//            //check same document and same token
//            if (annotatorState.getSelection().getBegin() == currentRecommendation.getOffset()
//                .getBeginCharacter()
//                && annotatorState.getSelection().getEnd() == currentRecommendation.getOffset()
//                .getEndCharacter()
//            ) {
//                moveToNextRecommendation(aEvent.getTarget());
//            }
//            aEvent.getTarget().add(mainContainer);
//        }
//    }

    @OnEvent
    public void onRecommendationRejectEvent(AjaxRecommendationRejectedEvent aEvent)
    {
        AnnotatorState annotatorState = getModelObject();
        AnnotatorState eventState = aEvent.getAnnotatorState();

        Predictions model = recommendationService.getPredictions(annotatorState.getUser(),
                annotatorState.getProject());

        if (
                alStateModel.getObject().isSessionActive() && 
                eventState.getUser().equals(annotatorState.getUser()) && 
                eventState.getProject().equals(annotatorState.getProject())
        ) {
            SourceDocument document = eventState.getDocument();
            VID vid = aEvent.getVid();
            Optional<AnnotationSuggestion> prediction = model.getPredictionByVID(document, vid);

            if (!prediction.isPresent()) {
                LOG.error("Could not find prediction in [{}] with id [{}]", document, vid);
                error("Could not find prediction");
                return;
            }

            AnnotationSuggestion rejectedRecommendation = prediction.get();
            applicationEventPublisherHolder.get().publishEvent(
                new ActiveLearningRecommendationEvent(this, eventState.getDocument(),
                    rejectedRecommendation, annotatorState.getUser().getUsername(),
                    eventState.getSelectedAnnotationLayer(), rejectedRecommendation.getFeature(),
                    REJECTED, model.getPredictionsByTokenAndFeature(
                    rejectedRecommendation.getDocumentName(),
                    eventState.getSelectedAnnotationLayer(),
                    rejectedRecommendation.getBegin(),
                    rejectedRecommendation.getEnd(),
                    rejectedRecommendation.getFeature())));

            if (
                    document.equals(annotatorState.getDocument()) && 
                    vid.getLayerId() == alStateModel.getObject().getLayer().getId() && 
                    prediction.get().equals(
                            alStateModel.getObject().getCurrentRecommendation().orElse(null))
            ) {
                moveToNextRecommendation(aEvent.getTarget());
            }
            aEvent.getTarget().add(mainContainer);
        }
    }

    @OnEvent
    public void onRecommendationAcceptEvent(AjaxRecommendationAcceptedEvent aEvent)
    {
        AnnotatorState annotatorState = ActiveLearningSidebar.this.getModelObject();
        Predictions model = recommendationService.getPredictions(annotatorState.getUser(),
                annotatorState.getProject());
        AnnotatorState eventState = aEvent.getAnnotatorState();
        SourceDocument document = annotatorState.getDocument();
        VID vid = aEvent.getVid();
        Optional<AnnotationSuggestion> oRecommendation = model.getPredictionByVID(document, vid);

        if (!oRecommendation.isPresent()) {
            LOG.error("Could not find prediction in [{}] with id [{}]", document, vid);
            error("Could not find prediction");
            aEvent.getTarget().addChildren(getPage(), IFeedback.class);
            return;
        }

        AnnotationSuggestion acceptedRecommendation = oRecommendation.get();
        
        model = recommendationService.getPredictions(annotatorState.getUser(),
                annotatorState.getProject());
        applicationEventPublisherHolder.get().publishEvent(
            new ActiveLearningRecommendationEvent(this, eventState.getDocument(),
                acceptedRecommendation, annotatorState.getUser().getUsername(),
                eventState.getSelectedAnnotationLayer(), acceptedRecommendation.getFeature(),
                ACCEPTED, model.getPredictionsByTokenAndFeature(
                acceptedRecommendation.getDocumentName(),
                eventState.getSelectedAnnotationLayer(),
                acceptedRecommendation.getBegin(),
                acceptedRecommendation.getEnd(),
                acceptedRecommendation.getFeature())));

        Optional<AnnotationSuggestion> currentRecommendation = alStateModel.getObject()
            .getCurrentRecommendation();
        if (
                alStateModel.getObject().isSessionActive() && 
                currentRecommendation.isPresent() && 
                eventState.getUser().equals(annotatorState.getUser()) && 
                eventState.getProject().equals(annotatorState.getProject())
        ) {
            if (
                    acceptedRecommendation.getOffset().equals(
                            currentRecommendation.get().getOffset()) && 
                    annotationService.getLayer(vid.getLayerId()).equals(
                            alStateModel.getObject().getLayer()) && 
                    acceptedRecommendation.getFeature().equals(
                            currentRecommendation.get().getFeature())
            ) {
                moveToNextRecommendation(aEvent.getTarget());
            }
            aEvent.getTarget().add(mainContainer);
        }
    }

    @OnEvent
    public void onRenderAnnotations(RenderAnnotationsEvent aEvent)
    {
        if (vMarkerType.equals(ANNOTATION_MARKER)) {
            if (highlightVID != null) {
                aEvent.getVDocument().add(new VAnnotationMarker(VMarker.FOCUS, highlightVID));
            }
        }
        else if (vMarkerType.equals(TEXT_MARKER)) {
            if (selectedRecord != null) {
                AnnotatorState annotatorState = ActiveLearningSidebar.this.getModelObject();
                if (annotatorState.getWindowBeginOffset() <= selectedRecord
                    .getOffsetCharacterBegin()
                    && selectedRecord.getOffsetCharacterEnd() <= annotatorState
                    .getWindowEndOffset()) {
                    aEvent.getVDocument().add(new VTextMarker(VMarker.FOCUS,
                        selectedRecord.getOffsetCharacterBegin() - annotatorState
                            .getWindowBeginOffset(),
                        selectedRecord.getOffsetCharacterEnd() - annotatorState
                            .getWindowBeginOffset()));
                }
            }
        }

        ActiveLearningUserState alState = alStateModel.getObject();
        if (
                alState.getCurrentRecommendation().isPresent() &&
                !alState.getCurrentRecommendation().get().isVisible()
        ) {
            // Obtain the next recommendation
            ActiveLearningStrategy activeLearningRecommender = alStateModel.getObject()
                .getStrategy();
            alState.setCurrentDifference(activeLearningRecommender.updateRecommendations(
                    learningRecordService, alState.getLearnSkippedRecommendationTime()));
            
            // If there is a new recommendation, then open it in the sidebar
            if (alState.getCurrentRecommendation().isPresent()) {
                alState.setHasUnseenRecommendation(true);
                
                refreshFeatureEditor(aEvent.getRequestHandler(),
                        alState.getCurrentRecommendation().get());
            }
            // If there there is now new recommendation, fall back to any skipped recommendations
            else if (alState.getLearnSkippedRecommendationTime() == null) {
                alState.setHasUnseenRecommendation(false);
                alState.setHasUnseenRecommendation(
                        alState.getStrategy()
                        .hasRecommendationWhichIsSkipped(learningRecordService,
                            activeLearningService));
            }
            // If there are no skipped recommendations either, stop
            else {
                alState.setHasUnseenRecommendation(false);
                alState.setHasSkippedRecommendation(false);
            }
        }
    }

    @OnEvent
    public void onPredictionsSwitched(AjaxPredictionsSwitchedEvent aEvent)
    {
        ActiveLearningUserState alState = alStateModel.getObject();

        if (alState.isSessionActive()) {
            alState.setListOfRecommendationsForEachToken(
                    activeLearningService.getRecommendationFromRecommendationModel(getModelObject(),
                            alState.getLayer()));

            Optional<Delta> recommendationDifference = alState.getStrategy()
                    .generateRecommendationWithLowestDifference(learningRecordService,
                            alState.getLearnSkippedRecommendationTime(),
                            alState.getListOfRecommendationsForEachToken());
            alState.setCurrentDifference(recommendationDifference);
        }
    }
}
