package edu.oswego.cs.rest;

public class Rating {
  private String movieTitle;
  private String ratingName;
  private String userRating;

  public Rating() {}

  public String getMovieTitle() {
    return movieTitle;
  }

  public void setMovieTitle(String movieTitle) {
    this.movieTitle = movieTitle;
  }

  public String getRatingName() {
    return ratingName;
  }

  public void setRatingName(String ratingName) {
    this.ratingName = ratingName;
  }

  public String getUserRating() {
    return userRating;
  }

  public void setUserRating(String userRating) {
    this.userRating = userRating;
  }
}