/*
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package play.core.buildlink;

public interface FailedBuild extends BuildResult {
  Throwable throwable();
}