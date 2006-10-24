/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */
package com.metavize.tran.util;


/**
 * I found myself creating many little classes (structs), to associate
 * two objects together.  This is a generic version of that
 * approach, useful for returns/arguments.
 * <br><br>
 * Example of use:
 * <code>
 * ArrayList&lt;Pair&lt;String,InetAddress>> myList = new ArrayList&lt;Pair&lt;String,InetAddress>>();<br>
 * myList.put(new Pair&lt;String, InetAddress>("localhost", InetAddress.getByName("localhost"));
 * </code>
 */
public class Pair<A,B> implements java.io.Serializable {

  public final A a;
  public final B b;

  public Pair(A a) {
    this.a = a;
    this.b = null;
  }

  public Pair(A a, B b) {
    this.a = a;
    this.b = b;
  }

}