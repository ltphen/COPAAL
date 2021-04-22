package org.dice_research.fc.paths;

import java.util.Collection;
import java.util.stream.Collectors;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.dice.FactCheck.Corraborative.UIResult.Path;
import org.dice.FactCheck.Corraborative.sum.ScoreSummarist;
import org.dice_research.fc.IFactChecker;
import org.dice_research.fc.data.FactCheckingResult;
import org.dice_research.fc.paths.filter.AlwaysTruePathFilter;
import org.dice_research.fc.paths.filter.AlwaysTrueScoreFilter;
import org.dice_research.fc.paths.filter.IPathFilter;
import org.dice_research.fc.paths.filter.IScoreFilter;

/**
 * This class implements the typical process for checking a given fact.
 * 
 * @author Michael R&ouml;der (michael.roeder@uni-paderborn.de)
 *
 */
public class PathBasedFactChecker implements IFactChecker {

  /**
   * The class that is used to search for (corroborative) paths.
   */
  protected IPathSearcher pathSearcher;
  /**
   * A class that can be used to filter paths.
   */
  protected IPathFilter pathFilter = new AlwaysTruePathFilter();
  /**
   * The path scorer that is used to score the single paths.
   */
  protected IPathScorer pathScorer;
  /**
   * A class that can be used to filter path scores.
   */
  protected IScoreFilter scoreFilter = new AlwaysTrueScoreFilter();
  /**
   * The class that is used to summarize the scores of the single paths to create a final score.
   */
  protected ScoreSummarist summarist;
  
  /**
   * Checks the given fact.
   * 
   * @param subject the subject of the fact to check
   * @param predicate the predicate of the fact to check
   * @param object the object of the fact to check
   * @return The result of the fact checking
   */
  @Override
  public FactCheckingResult check(Resource subject, Property predicate, Resource object) {
    // Get a list of potential paths
    Collection<Path> paths = pathSearcher.search(subject, predicate, object);

    // Filter paths, score the paths with respect to the given triple and filter them again based on
    // the score
    paths = paths.parallelStream().filter(pathFilter)
        .map(p -> pathScorer.score(subject, predicate, object, p))
        .filter(p -> scoreFilter.test(p.getPathScore())).collect(Collectors.toList());

    // Get the scores
    double[] scores = paths.stream().mapToDouble(p -> p.getPathScore()).toArray();

    // Summarize the scores
    double veracity = summarist.summarize(scores);

    // FIXME Add veracity score and evidences (i.e., paths)
    return new FactCheckingResult();
  }
}
