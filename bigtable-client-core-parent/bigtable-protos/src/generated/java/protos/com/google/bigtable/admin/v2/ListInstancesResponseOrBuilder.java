// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/bigtable/admin/v2/bigtable_instance_admin.proto

package com.google.bigtable.admin.v2;

public interface ListInstancesResponseOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.bigtable.admin.v2.ListInstancesResponse)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>repeated .google.bigtable.admin.v2.Instance instances = 1;</code>
   *
   * <pre>
   * The list of requested instances.
   * </pre>
   */
  java.util.List<com.google.bigtable.admin.v2.Instance> 
      getInstancesList();
  /**
   * <code>repeated .google.bigtable.admin.v2.Instance instances = 1;</code>
   *
   * <pre>
   * The list of requested instances.
   * </pre>
   */
  com.google.bigtable.admin.v2.Instance getInstances(int index);
  /**
   * <code>repeated .google.bigtable.admin.v2.Instance instances = 1;</code>
   *
   * <pre>
   * The list of requested instances.
   * </pre>
   */
  int getInstancesCount();
  /**
   * <code>repeated .google.bigtable.admin.v2.Instance instances = 1;</code>
   *
   * <pre>
   * The list of requested instances.
   * </pre>
   */
  java.util.List<? extends com.google.bigtable.admin.v2.InstanceOrBuilder> 
      getInstancesOrBuilderList();
  /**
   * <code>repeated .google.bigtable.admin.v2.Instance instances = 1;</code>
   *
   * <pre>
   * The list of requested instances.
   * </pre>
   */
  com.google.bigtable.admin.v2.InstanceOrBuilder getInstancesOrBuilder(
      int index);

  /**
   * <code>repeated string failed_locations = 2;</code>
   *
   * <pre>
   * Locations from which Instance information could not be retrieved,
   * due to an outage or some other transient condition.
   * Instances whose Clusters are all in one of the failed locations
   * may be missing from `instances`, and Instances with at least one
   * Cluster in a failed location may only have partial information returned.
   * </pre>
   */
  com.google.protobuf.ProtocolStringList
      getFailedLocationsList();
  /**
   * <code>repeated string failed_locations = 2;</code>
   *
   * <pre>
   * Locations from which Instance information could not be retrieved,
   * due to an outage or some other transient condition.
   * Instances whose Clusters are all in one of the failed locations
   * may be missing from `instances`, and Instances with at least one
   * Cluster in a failed location may only have partial information returned.
   * </pre>
   */
  int getFailedLocationsCount();
  /**
   * <code>repeated string failed_locations = 2;</code>
   *
   * <pre>
   * Locations from which Instance information could not be retrieved,
   * due to an outage or some other transient condition.
   * Instances whose Clusters are all in one of the failed locations
   * may be missing from `instances`, and Instances with at least one
   * Cluster in a failed location may only have partial information returned.
   * </pre>
   */
  java.lang.String getFailedLocations(int index);
  /**
   * <code>repeated string failed_locations = 2;</code>
   *
   * <pre>
   * Locations from which Instance information could not be retrieved,
   * due to an outage or some other transient condition.
   * Instances whose Clusters are all in one of the failed locations
   * may be missing from `instances`, and Instances with at least one
   * Cluster in a failed location may only have partial information returned.
   * </pre>
   */
  com.google.protobuf.ByteString
      getFailedLocationsBytes(int index);

  /**
   * <code>optional string next_page_token = 3;</code>
   *
   * <pre>
   * Set if not all instances could be returned in a single response.
   * Pass this value to `page_token` in another request to get the next
   * page of results.
   * </pre>
   */
  java.lang.String getNextPageToken();
  /**
   * <code>optional string next_page_token = 3;</code>
   *
   * <pre>
   * Set if not all instances could be returned in a single response.
   * Pass this value to `page_token` in another request to get the next
   * page of results.
   * </pre>
   */
  com.google.protobuf.ByteString
      getNextPageTokenBytes();
}
