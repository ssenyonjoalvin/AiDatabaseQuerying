package org.pahappa.systems.aiquery.web;


import org.pahappa.systems.aiquery.agent.AgenticQueryOrchestrator;
import org.pahappa.systems.aiquery.client.AiChatClient;
import org.pahappa.systems.aiquery.config.AiQueryProperties;
import org.pahappa.systems.aiquery.dto.AskRequest;
import org.pahappa.systems.aiquery.dto.AskResponse;
import org.pahappa.systems.aiquery.dto.QueryResult;
import org.pahappa.systems.aiquery.exception.AiQueryAuthorizationException;
import org.pahappa.systems.aiquery.exception.AiQueryExecutionException;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.service.AiQueryService;
import org.pahappa.systems.aiquery.service.QueryResultSynthesizer;
import org.sers.webutils.model.security.User;
import org.sers.webutils.server.shared.SharedAppData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;

@Controller("aiQueryEngineController")
public class AiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiController.class);

    private final AiQueryService aiQueryService;
    private final AiChatClient aiChatClient;
    private final AiQueryProperties aiQueryProperties;
    private final QueryResultSynthesizer resultSynthesizer;
    private final AgenticQueryOrchestrator agenticQueryOrchestrator;

    @Autowired
    public AiController(AiQueryService aiQueryService, AiChatClient aiChatClient, AiQueryProperties aiQueryProperties,
                         QueryResultSynthesizer resultSynthesizer, AgenticQueryOrchestrator agenticQueryOrchestrator) {
        this.aiQueryService = aiQueryService;
        this.aiChatClient = aiChatClient;
        this.aiQueryProperties = aiQueryProperties;
        this.resultSynthesizer = resultSynthesizer;
        this.agenticQueryOrchestrator = agenticQueryOrchestrator;
    }

    @RequestMapping( value = "/ai-query/ask", method = RequestMethod.POST  )
    @ResponseBody
    public ResponseEntity<Object> ask(@RequestBody AskRequest request) {
        User user = SharedAppData.getLoggedInUser();
        String question = request.getQuestion();

        try {
            QueryResult result = aiQueryProperties.isAgentEnabled()
                    ? agenticQueryOrchestrator.run(user, question).result()
                    : aiQueryService.queryFromNaturalLanguage(user, question);
            String answer = resultSynthesizer.synthesize(question, result);
            return new ResponseEntity<Object>(new AskResponse(answer, result), HttpStatus.OK);
        } catch (AiQueryAuthorizationException e) {
            return new ResponseEntity<Object>(
                    Collections.singletonMap("message", e.getMessage()), HttpStatus.FORBIDDEN);
        } catch (AiQueryValidationException e) {
            // The question didn't map to any queryable entity/field -- treat it as a
            // general question for the assistant rather than a failed data query.
            try {
                String answer = aiChatClient.generate(aiQueryProperties.getAdvisorySystemPrompt(), question);
                return new ResponseEntity<Object>(Collections.singletonMap("answer", answer), HttpStatus.OK);
            } catch (Exception fallbackFailure) {
                LOGGER.warn("Advisory fallback failed for question '{}': {}", question, fallbackFailure.getMessage());
                return new ResponseEntity<Object>(
                        Collections.singletonMap("message", e.getMessage()), HttpStatus.BAD_REQUEST);
            }
        } catch (AiQueryExecutionException e) {
            LOGGER.warn("AI query execution failed for question '{}': {}", question, e.getMessage());
            return new ResponseEntity<Object>(
                    Collections.singletonMap("message", "Could not execute that query. Please try rephrasing it."),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            LOGGER.error("Unexpected error handling AI query '{}'", question, e);
            return new ResponseEntity<Object>(
                    Collections.singletonMap("message", "Something went wrong answering that question. Please try again."),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
