package org.dice_research.fc.config;

import java.io.FileNotFoundException;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.lang.sparql_11.ParseException;
import org.dice_research.fc.IFactChecker;
import org.dice_research.fc.data.FactCheckingResult;
import org.dice_research.fc.paths.verbalizer.IPathVerbalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*", allowCredentials = "true")
@RequestMapping("/api/v1/")
public class RESTController {
  
  @Autowired
  ApplicationContext ctx;

  private static final Logger LOGGER = LoggerFactory.getLogger(RESTController.class);

  @GetMapping("/test")
  public String ping(){
    return "OK!";
  }

  @RequestMapping("/error")
  @ResponseBody
  public String error(){
    LOGGER.info("Error Occured");
    return "Error occured Only god knows why :))))";
  }

  @GetMapping("/validate")
  public FactCheckingResult validate(
      @RequestParam(value = "subject", required = true) String subject,
      @RequestParam(value = "object", required = true) String object,
      @RequestParam(value = "property", required = true) String property,
      RequestParameters details)
      throws InterruptedException, FileNotFoundException, ParseException {

    Resource subjectURI = ResourceFactory.createResource(subject);
    Resource objectURI = ResourceFactory.createResource(object);
    Property propertyURI = ResourceFactory.createProperty(property);
    
    // perform fact-check
    IFactChecker factChecker = ctx.getBean(IFactChecker.class);
    FactCheckingResult result = factChecker.check(subjectURI, propertyURI, objectURI);
    
    // verbalize result
    IPathVerbalizer verbalizer = ctx.getBean(IPathVerbalizer.class, ctx.getBean(QueryExecutionFactory.class), details);
    verbalizer.verbalizeResult(result);
    return result;
  }

}
