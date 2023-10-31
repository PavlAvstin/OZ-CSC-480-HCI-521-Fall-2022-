package edu.oswego.cs.rest;

import com.mongodb.client.model.*;
import edu.oswego.cs.rest.JsonClasses.Rating;
import edu.oswego.cs.rest.JsonClasses.Tag;
import org.bson.BsonDateTime;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DatabaseController {
  String mongoDatabaseName = System.getenv("MONGO_MOVIE_DATABASE_NAME");
  String mongoURL = System.getenv("MONGO_MOVIE_URL");

  /*
   *
   */
  public MongoDatabase getMovieDatabase() {
    MongoClient mongoClient = MongoClients.create(mongoURL);
    return mongoClient.getDatabase(mongoDatabaseName);
  }

  /*
   * Database get collection methods
   */
  public MongoCollection<Document> getMovieCollection() {
    return getMovieDatabase().getCollection("movies");
  }

  public MongoCollection<Document> getRatingCollection() {
    return getMovieDatabase().getCollection("ratings");
  }

  public MongoCollection<Document> getTagCollection() {
    return getMovieDatabase().getCollection("tags");
  }

  /*
   * Rating Create functions
   *
   * createRating
   */
    /**
   * Creates and adds a rating object associated with a movie to the database. Employs a series
   * of checks to make sure the movie exists and the ratingCategory does not already exist.
   * @param ratingName Name of the rating category. For example, "How Harrison Ford is it", "Stickiness"
   * @param movieIdHexString movie unique MongoDB identifier
   * @param username user to associate with the rating
   * @param userRating value assigned by the user
   * @param upperbound upperbound of the rating scale. 0 < upperbound < 11
   */
  public void createRating(String ratingName, String userRating, String upperbound, String username,
                           String movieIdHexString, String privacy){
    // get collections
    MongoCollection<Document> ratingCollection = getRatingCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    // check if the user rating is between 1 and the upperbound
    if (!(Integer.valueOf(userRating) <= Integer.valueOf(upperbound) && Integer.valueOf(userRating) >= 1))
      return;

    // attempt to get the rating if the user has already created one for this category and upperbound on the movie
    Bson upperBoundFilter = Filters.eq("upperbound", upperbound);
    Bson ratingNameFilter = Filters.eq("ratingName", ratingName);
    Bson usernameFilter = Filters.eq("username", username);
    Bson movieFilter = Filters.eq("movieId", movieIdHexString);
    Document rating = ratingCollection.find(Filters.and(usernameFilter, ratingNameFilter, upperBoundFilter, movieFilter)).first();
    // attempt to get the corresponding movie
    Document movie = getMovieDocumentWithHexId(movieIdHexString);

    // check to see if movie exists and rating is not already created by user
    if (rating == null && movie != null) {
      Document newRating = new Document("userName", username)
                  .append("ratingName", ratingName)
                  .append("userRating", userRating)
                  .append("upperbound", upperbound)
                  .append("movieTitle", movie.get("title"))
                  .append("movieId", movieIdHexString)
                  .append("dateTimeCreated", new BsonDateTime(System.currentTimeMillis()))
                  .append("privacy", privacy);
      ratingCollection.insertOne(newRating);

      Bson ratingCategoryMovieFilter = Filters.eq("ratingCategoryNames", ratingName);
      ObjectId movieId = new ObjectId(movieIdHexString);
      Bson movieIdFilter = Filters.eq("_id", movieId);
      Document movieWithRatingCategory = movieCollection.find(Filters.and(ratingCategoryMovieFilter, movieIdFilter)).first();
      // check to see if movie category needs to be pushed
      if (movieWithRatingCategory == null) {
        Bson movieRatingCategoryUpdateOperation = Updates.push("ratingCategoryNames", ratingName);
        movieCollection.updateOne(movie, movieRatingCategoryUpdateOperation);
      }
    }
  }

  /*
   * Rating Get methods
   *
   * getRatingsWithFilter
   *
   * getRatingsWithSameNameAndUpperbound
   * getRatingsWithSameName
   * getRatingsWithMovieId
   * getMostPopularAggregatedRatingForMovie
   */

  /**
   * Creates and returns a list of ratings that match the given filter. This is called by many of the
   * other get functions.
   * @param ratingsCollection
   * @param filter
   * @return
   */
  private static ArrayList<Rating> getRatingsWithFilter(MongoCollection<Document> ratingsCollection, Bson filter) {
    var ratings = ratingsCollection.find(filter).map(document -> {
      var ra = new Rating();
      ra.setRatingName(document.getString("ratingName"));
      ra.setUserRating(document.getString("userRating"));
      ra.setMovieTitle(document.getString("movieTitle"));
      ra.setDateTimeCreated(document.get("dateTimeCreated").toString());
      ra.setPrivacy(document.getString("privacy"));
      ra.setMovieId(document.getString("movieId"));
      ra.setUpperbound(document.getString("upperbound"));
      return ra;
    });
    var list = new ArrayList<Rating>();
    ratings.forEach(list::add);
    return list;
  }

  public List<Rating> getRatingsWithSameNameAndUpperbound(String ratingName, String upperbound) {
    var ratings = getRatingCollection();
    var ratingNameFilter = Filters.eq("ratingName", ratingName);
    var upperboundFilter = Filters.eq("upperbound", upperbound);
    var filter = Filters.and(ratingNameFilter, upperboundFilter);
    return getRatingsWithFilter(ratings, filter);
  }

  public List<Rating> getRatingsWithSameName(String ratingName) {
    var ratings = getRatingCollection();
    var ratingNameFilter = Filters.eq("ratingName", ratingName);
    var filter = Filters.eq(ratingNameFilter);
    return getRatingsWithFilter(ratings, filter);
  }

  public List<Rating> getRatingsWithMovieId(String movieId){
    var ratings = getRatingCollection();
    var movieIdFilter = Filters.eq("movieId", movieId);
    return getRatingsWithFilter(ratings, movieIdFilter);
  }

  /**
   * Finds the most popular upperbound of the most popular rating name and calculates the average
   * of the userRatings
   * @param movieId MongoDB HexId of the movie to search for ratings from
   * @return A Rating object with the most popular category name, an upperbound of the most common
   * upperbound, and a userRating of the average of all userRatings for the category and upperbound.
   */
  public Rating getMostPopularAggregatedRatingForMovie(String movieId) {
    MongoCollection<Document> ratingCollection = getRatingCollection();
    // get the most popular rating category name for the movie
    Document ratingNameDoc = ratingCollection.aggregate(
            Arrays.asList(
                    Aggregates.match(Filters.eq("movieId", movieId)),
                    Aggregates.group("$ratingName", Accumulators.sum("count", 1)),
                    Aggregates.sort(Sorts.descending("count"))
            )
    ).first();

    // gets the most popular upperbound for the category
    String mostPopularCategoryName = ratingNameDoc.getString("_id");
    Document ratingScaleDoc = ratingCollection.aggregate(
            Arrays.asList(
                    Aggregates.match(Filters.and(Filters.eq("movieId", movieId), Filters.eq("ratingName", mostPopularCategoryName))),
                    Aggregates.group("$upperbound", Accumulators.sum("count", 1)),
                    Aggregates.sort(Sorts.descending("count"))
            )
    ).first();
    String mostPopularCategoryUpperbound = ratingScaleDoc.getString("_id");

    int userRatingSum = 0;
    // using the most popular name and most popular upperbound go through and collect the sum of all the user ratings
    // gets the most popular upperbound for the category
    for (Document doc : ratingCollection.find(Filters.and(Filters.eq("movieId", movieId), Filters.eq("ratingName", mostPopularCategoryName), Filters.eq("upperbound", mostPopularCategoryUpperbound)))) {
      userRatingSum = userRatingSum + Integer.parseInt(doc.getString("userRating"));
    }
    int count = ratingScaleDoc.getInteger("count");
    double average = ((double) userRatingSum ) / count;

    // create a rating object that has the most popular name, upperbound, and a userRating of the average of all
    //  the ratings of that name with that upperbound.
    //  TODO consider collecting for all ratings of this name which would take some normalizing. Is this worth it?
    Rating rating = new Rating();
    rating.setRatingName(mostPopularCategoryName);
    rating.setUpperbound(mostPopularCategoryUpperbound);
    rating.setUserRating(Double.toString(average));
    return rating;
  }

  /*
   * Rating Update functions
   */

  /*
   * Rating Delete functions
   */




  /*
   * Tag Create functions
   *
   * createTags
   */
  /**
   * Users are not allowed to create a tag for a movie that does not already exist, or the same tag for the same movie.
   * Otherwise, duplicate tags are allowed by multiple users due to privacy issues
   *
   * @param tagName name of tag used to access and store the information
   * @param movieIdHexString MongoDB unique identifier for the movie to attach the tag to
   * @param username name of the user trying to create the tag
   * @param privacy privacy setting of the tag whether it is private, friends-only, or public
   */
  public void createTag(String tagName, String movieIdHexString, String username, String privacy){
    // get the collections
    MongoCollection<Document> tagCollection = getTagCollection();
    MongoCollection<Document> movieCollection = getMovieCollection();

    // attempt to grab the movie using its unique MongoDB id
    ObjectId movieId = new ObjectId(movieIdHexString);
    Document movie = movieCollection.find(Filters.eq("_id", movieId)).first();

    // if the movie does not exist move on
    if (movie == null) { return; }

    // attempt to grab the tag in a few different types
    Bson movieTitleFilter = Filters.eq("movieTitle", movie.get("title"));
    Bson usernameFilter = Filters.eq("username", username);
    Bson tagNameFilter = Filters.eq("tagName", tagName);

    Document taggedWithMovieByUser = tagCollection.find(Filters.and(movieTitleFilter,usernameFilter, tagNameFilter)).first();

    // if you have already tagged this movie this tag
    if(taggedWithMovieByUser != null){

    } // else the tag does not exist
    else{
      // create and add the tag
      Document newTag = new Document("userName", username)
              .append("tagName", tagName)
              .append("movieTitle", movie.get("title"))
              .append("movieId", movieIdHexString)
              .append("dateTimeCreated", new BsonDateTime(System.currentTimeMillis()))
              .append("privacy", privacy);
      // add to the database
      tagCollection.insertOne(newTag);

      Bson tagMovieFilter = Filters.eq("tagNames", tagName);
      Bson movieIdFilter = Filters.eq("_id", movieId);
      Document movieWithTag = movieCollection.find(Filters.and(tagMovieFilter, movieIdFilter)).first();
      // check to see if movie category needs to be pushed
      if (movieWithTag == null) {
        Bson movieRatingCategoryUpdateOperation = Updates.push("tagNames", tagName);
        movieCollection.updateOne(movie, movieRatingCategoryUpdateOperation);
      }
    }

  }

  /*
   * Tag Get functions
   *
   * getTagsWithFilter
   * getTagsByMovieId
   */

  /**
   * Creates and returns a list of ratings that match the given filter. This is called by many of the other
   * get methods for tags.
   * @param tagCollection
   * @param filter
   * @return
   */
  private static ArrayList<Tag> getTagsWithFilter(MongoCollection<Document> tagCollection, Bson filter) {
    var ratings = tagCollection.find(filter).map(document -> {
      var tag = new Tag();
      tag.setTagName(document.getString("tagName"));
      tag.setMovieTitle(document.getString("movieTitle"));
      tag.setUsername(document.getString("username"));
      tag.setPrivacy(document.getString("privacy"));
      tag.setDateTimeCreated(document.get("dateTimeCreated").toString());
      return tag;
    });
    var list = new ArrayList<Tag>();
    ratings.forEach(list::add);
    return list;
  }

  public List<Tag> getTagsWithMovieId(String movieId) {
    var reviews = getTagCollection();
    var filter = Filters.eq("movieId", movieId);
    return getTagsWithFilter(reviews, filter);
  }

  /*
   * Tag Update Functions
   */

  /*
   * Tag Delete Functions
   */


  /*
   * Other helper functions. These are used to support the base CRUD functions for each database object
   *
   * getMovieDocumentWithHexId
   */
  /**
   * retrieves movie using MongoDB unique hex identifier. Creates a ObjectID object to return the movie Document.
   * @param hexID String representation of the hex id.
   * @return Document of the movie matching the id, null if id is not found
   */
    public Document getMovieDocumentWithHexId(String hexID){
      MongoCollection<Document> movieCollection = getMovieCollection();
      ObjectId movieId = new ObjectId(hexID);
      return movieCollection.find(Filters.eq("_id", movieId)).first();
    }
}