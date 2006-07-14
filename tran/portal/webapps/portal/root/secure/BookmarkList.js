// Copyright (c) 2006 Metavize Inc.
// All rights reserved.

function BookmarkList(parent)
{
  if (0 == arguments.length) {
    return;
  }

  var header = [];
  var hi = new DwtListHeaderItem("application", "Application", null, 100, true, true, true);
  hi.memberName = "app";
  header.push(hi);
  hi = new DwtListHeaderItem("name", "Name", null, 200, true, true, true);
  hi.memberName = "name";
  header.push(hi);

  DwtListView.call(this, parent, null, DwtControl.ABSOLUTE_STYLE, header);

  this.setUI(0);
};

BookmarkList.prototype = new DwtListView();
BookmarkList.prototype.constructor = BookmarkList;

// public methods -------------------------------------------------------------

BookmarkList.prototype.refresh = function()
{
  var cb = new AjxCallback(this, this._bookmarkListCallback, { })

  AjxRpc.invoke(null, "secure/bookmark?command=ls", null, cb, true);
};

// internal methods -----------------------------------------------------------

BookmarkList.prototype._setListingXml = function(dom)
{
  var root = dom.getElementsByTagName("bookmarks")[0];

  var children = root.childNodes;

  var listing = new AjxVector();

  for (var i = 0; i < children.length; i++) {
    var child = children[i];

    if ("bookmark" == child.tagName) {
      listing.add(new Bookmark(child.getAttribute("id"),
                               child.getAttribute("name"),
                               child.getAttribute("app"),
                               child.getAttribute("target")));
    }
  }

  this.set(listing);
};

// DwtListView methods --------------------------------------------------------

BookmarkList.prototype._createItemHtml = function(item)
{
  var div = document.createElement("div");
  var base = "Row";
  div._styleClass = base;
  div._selectedStyleClass = [base, DwtCssStyle.SELECTED].join("-");

  this.associateItemWithElement(item, div, DwtListView.TYPE_LIST_ITEM);
  div.className = div._styleClass;

  var htmlArr = new Array();
  var idx = 0;

  // Table
  htmlArr[idx++] = "<table cellpadding=0 cellspacing=0 border=0";
  htmlArr[idx++] = this._noMaximize ? ">" : " width=100%>";

  // Row
  htmlArr[idx++] = "<tr>";

  // Data
  for (var j = 0; j < this._headerList.length; j++) {
    var col = this._headerList[j];

    if (!col._visible) {
      continue;
    }

    htmlArr[idx++] = "<td";
    var width = AjxEnv.isIE ? (col._width + 4) : col._width;
    htmlArr[idx++] = width ? (" width=" + width + ">") : ">";
    htmlArr[idx++] = "<div";
    htmlArr[idx++] = width ? " style='width: " + width + "'>" : ">";

    var value;
    if ("app" == col.memberName) {
      var app = portal.getApplication(item.app);
      if (app) {
        var iconUrl = app.getIconUrl();
        value = "<img src='" + iconUrl + "'/>";
      } else {
        // XXX put generic icon instead
      }
    } else if ("name" == col.memberName) {
      value = "<div class='BookmarkListName'>" + item[col.memberName] + "</div>";
    } else {
      value = item[col.memberName];
    }
    htmlArr[idx++] = (value || "") + "</div></td>";
  }

  htmlArr[idx++] = "</tr></table>";

  div.innerHTML = htmlArr.join("");

  return div;
};

BookmarkList.prototype._sortColumn = function(col, asc)
{
  this._lastSortCol = col;
  this._lastSortAsc = asc;

  var fn = function(a, b) {
    av = a[col.memberName];
    bv = b[col.memberName];

    return (asc ? 1 : -1) * (a < b ? -1 : (a > b ? 1 : 0));
  }

  this.getList().sort(fn);
  delete fn;

  this.setUI(0);
};

// callbacks ------------------------------------------------------------------

BookmarkList.prototype._bookmarkListCallback = function(obj, results)
{
  // XXX if error, show login dialog
  this._setListingXml(results.xml);
  this.setUI(1);
};

BookmarkList.prototype._mouseOverAction = function(ev, div)
{
  var item = this.getItemFromElement(div);
  this._mouseOverItem = item;

  if (div._type == DwtListView.TYPE_LIST_ITEM) {
    this.setToolTipContent(item.target);
  }
};

BookmarkList.prototype._mouseOutAction = function(mouseEv, div)
{
  this._mouseOverItem = null;
}
