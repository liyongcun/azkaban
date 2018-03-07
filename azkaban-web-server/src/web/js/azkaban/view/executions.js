/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

$.namespace('azkaban');

var successHandler = function (data) {
    console.log("cancel clicked");
    if (data.error) {
        showDialog("Error", data.error);
    }
    else {
        showDialog("Cancelled", "Flow has been cancelled.");

    }
};

function killFlow(execId) {
  var requestURL = document.location.href.replace("#currently-running", "");
  var requestData = {"execid": execId, "ajax": "cancelFlow"};
  ajaxCall(requestURL, requestData, successHandler);
}
var showDialog = function (title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal();
};

var executionsTabView;
azkaban.ExecutionsTabView = Backbone.View.extend({
  events: {
    'click #currently-running-view-link': 'handleCurrentlyRunningViewLinkClick',
    'click #recently-finished-view-link': 'handleRecentlyFinishedViewLinkClick',
    'click #setting-view-link':'handleReDoClick'
  },

  initialize: function (settings) {
    var selectedView = settings.selectedView;
    var requestURL = document.location.href;
      var act_reload = function (data) {
          if (data.error) {
              showDialog("Error", data.error);
          }
          else {
              showDialog("reloadJobTypePlugins","Successed !!!");
          }
      };
      var act_rechange = function (data) {
          if (data.error) {
              showDialog("Error", data.error);
          }
          else {
              showDialog("change cfg","Successed !!!");
          }
      };
      var act_reloadExecu = function (data) {
          if (data.error) {
              showDialog("Error", data.error);
          }
          else {
              showDialog("Reload Executors","Successed !!!");
          }
      };

    if (requestURL.indexOf('#') >0) {
          requestURL =requestURL.substr(0, document.location.href.indexOf('#'));
      }
    if (selectedView == 'recently-finished') {
      this.handleRecentlyFinishedViewLinkClick();
    }
    else {
      if (selectedView == 'currently-running') {
        this.handleCurrentlyRunningViewLinkClick();
      } else {
        this.handleReDoClick();
      }
    };
   $('#reload_plugins').click(function () {
       var requestData = {"action": "reloadJobTypePlugins"};
       console.log(requestURL)
       ajaxCall(requestURL, requestData, act_reload);
   });
    $('#submit_change').click(function () {
        var selectedflag=$('#bak_info input:radio:checked').val();
        console.log("submit_change" +selectedflag );
        var requestData = {"action": "change_cfg","bak":selectedflag };
        ajaxCall(requestURL, requestData, act_rechange);
        $('#submit_change').attr("data-dismiss" ,"modal");
   });

   $('#reload_executor').click(function () {
       var requestData = {"ajax": "reloadExecutors"};
       ajaxCall(requestURL, requestData,act_reloadExecu);
   });
  },
  render: function () {
  },
  handleCurrentlyRunningViewLinkClick: function () {
    $('#recently-finished-view-link').removeClass('active');
    $('#recently-finished-view').hide();
    $('#setting-view-link').removeClass('active');
    $('#setting-view').hide();
    $('#currently-running-view-link').addClass('active');
    $('#currently-running-view').show();
  },

  handleRecentlyFinishedViewLinkClick: function () {
    $('#currently-running-view-link').removeClass('active');
    $('#currently-running-view').hide();
    $('#setting-view-link').removeClass('active');
    $('#setting-view').hide();
    $('#recently-finished-view-link').addClass('active');
    $('#recently-finished-view').show();
  },
  handleReDoClick: function () {
      $('#currently-running-view-link').removeClass('active');
      $('#currently-running-view').hide();
      $('#recently-finished-view-link').removeClass('active');
      $('#recently-finished-view').hide();
      $('#setting-view-link').addClass('active');
      $('#setting-view').show();
  }
});

$(function () {
  executionsTabView = new azkaban.ExecutionsTabView({el: $('#header-tabs')});
  if (window.location.hash) {
    var hash = window.location.hash;
    if (hash == '#recently-finished') {
      executionsTabView.handleRecentlyFinishedViewLinkClick();
    }
  }
});
