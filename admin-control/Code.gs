var USERS_SHEET = 'Users';
var ROLL_BLOCKS_SHEET = 'RollBlocks';
var AUDIT_LOG_SHEET = 'AuditLog';

var USERS_HEADERS = [
  'installId',
  'rollNo',
  'firstSeenAt',
  'lastSeenAt',
  'appVersion',
  'deviceModel',
  'manufacturer',
  'androidVersion',
  'packageName'
];

var ROLL_BLOCK_HEADERS = [
  'rollNo',
  'isBlocked',
  'blockMode',
  'note',
  'updatedAt',
  'updatedBy'
];

var AUDIT_LOG_HEADERS = [
  'action',
  'payloadSummary',
  'updatedBy',
  'timestamp'
];

function doGet() {
  var controlState = getControlState_();
  return jsonResponse({
    success: true,
    appEnabled: controlState.appEnabled,
    maintenanceMessage: controlState.maintenanceMessage,
    updatedAt: controlState.updatedAt,
    updatedBy: controlState.updatedBy,
    message: 'GIET ERP control service is running.'
  });
}

function doPost(e) {
  var payload = parsePayload_(e);
  var action = String(payload.action || '').trim();

  if (!action) {
    return jsonResponse({
      success: false,
      message: 'Missing action.'
    });
  }

  try {
    ensureSheets_();

    switch (action) {
      case 'get_app_control':
        return jsonResponse(handleGetAppControl_());
      case 'register_install':
        return jsonResponse(handleRegisterInstall_(payload));
      case 'heartbeat':
        return jsonResponse(handleHeartbeat_(payload));
      case 'get_roll_access':
        return jsonResponse(handleGetRollAccess_(payload));
      case 'list_users':
        requireAdminSecret_(payload);
        return jsonResponse(handleListUsers_());
      case 'list_roll_blocks':
        requireAdminSecret_(payload);
        return jsonResponse(handleListRollBlocks_());
      case 'set_roll_blocks':
        requireAdminSecret_(payload);
        return jsonResponse(handleSetRollBlocks_(payload));
      case 'set_app_control':
        requireAdminSecret_(payload);
        return jsonResponse(handleSetAppControl_(payload));
      default:
        return jsonResponse({
          success: false,
          message: 'Unsupported action: ' + action
        });
    }
  } catch (error) {
    return jsonResponse({
      success: false,
      message: error.message || String(error)
    });
  }
}

function handleRegisterInstall_(payload) {
  upsertUserRow_(payload);
  appendAuditLog_('register_install', summarizePayload_(payload), payload.rollNo || 'public');
  return {
    success: true,
    message: 'Install registered.'
  };
}

function handleGetAppControl_() {
  var controlState = getControlState_();
  return {
    success: true,
    appEnabled: controlState.appEnabled,
    maintenanceMessage: controlState.maintenanceMessage,
    updatedAt: controlState.updatedAt,
    updatedBy: controlState.updatedBy
  };
}

function handleHeartbeat_(payload) {
  upsertUserRow_(payload);
  return {
    success: true,
    message: 'Heartbeat recorded.'
  };
}

function handleGetRollAccess_(payload) {
  var rollNo = normalizeRollNo_(payload.rollNo);
  var blockEntry = getBlockedRollMap_()[rollNo];
  var isBlocked = !!(blockEntry && blockEntry.isBlocked);
  return {
    success: true,
    rollNo: rollNo,
    isBlocked: isBlocked,
    canViewDetails: !isBlocked,
    blockMode: blockEntry ? blockEntry.blockMode : '',
    message: isBlocked
      ? (blockEntry.note || 'Access to ERP details is currently restricted for this roll number.')
      : 'Access allowed.'
  };
}

function handleListUsers_() {
  var controlState = getControlState_();
  var blockedMap = getBlockedRollMap_();
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(USERS_SHEET);
  var rows = readRows_(sheet);
  var users = rows.map(function(row) {
    return {
      installId: row.installId || '',
      rollNo: row.rollNo || '',
      firstSeenAt: row.firstSeenAt || '',
      lastSeenAt: row.lastSeenAt || '',
      appVersion: row.appVersion || '',
      deviceModel: row.deviceModel || '',
      manufacturer: row.manufacturer || '',
      androidVersion: row.androidVersion || '',
      packageName: row.packageName || '',
      isBlocked: !!blockedMap[normalizeRollNo_(row.rollNo)]
    };
  }).sort(function(a, b) {
    return String(b.lastSeenAt || '').localeCompare(String(a.lastSeenAt || ''));
  });

  return {
    success: true,
    appControl: controlState,
    users: users
  };
}

function handleListRollBlocks_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(ROLL_BLOCKS_SHEET);
  var rows = readRows_(sheet);
  var blockedRolls = rows.filter(function(row) {
    return String(row.isBlocked).toLowerCase() === 'true';
  }).map(function(row) {
    return {
      rollNo: row.rollNo || '',
      isBlocked: String(row.isBlocked).toLowerCase() === 'true',
      blockMode: row.blockMode || 'details_only',
      note: row.note || '',
      updatedAt: row.updatedAt || '',
      updatedBy: row.updatedBy || ''
    };
  });

  return {
    success: true,
    blockedRolls: blockedRolls
  };
}

function handleSetRollBlocks_(payload) {
  var rollNos = payload.rollNos || [];
  if (!rollNos.length) {
    throw new Error('No roll numbers provided.');
  }

  var isBlocked = !!payload.isBlocked;
  var note = String(payload.note || '');
  var blockMode = String(payload.blockMode || 'details_only');
  var updatedBy = String(payload.updatedBy || 'GietErpAdmin');
  var timestamp = isoTimestamp_();

  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(ROLL_BLOCKS_SHEET);
  var rows = readRows_(sheet);
  var rowIndexByRoll = {};
  rows.forEach(function(row, index) {
    rowIndexByRoll[normalizeRollNo_(row.rollNo)] = index + 2;
  });

  rollNos.forEach(function(rawRollNo) {
    var rollNo = normalizeRollNo_(rawRollNo);
    if (!rollNo) {
      return;
    }
    var values = [
      rollNo,
      isBlocked,
      blockMode,
      note,
      timestamp,
      updatedBy
    ];
    if (rowIndexByRoll[rollNo]) {
      sheet.getRange(rowIndexByRoll[rollNo], 1, 1, values.length).setValues([values]);
    } else {
      sheet.appendRow(values);
    }
  });

  appendAuditLog_(
    'set_roll_blocks',
    JSON.stringify({
      rollNos: rollNos,
      isBlocked: isBlocked,
      blockMode: blockMode
    }),
    updatedBy
  );

  return {
    success: true,
    message: 'Roll block list updated.'
  };
}

function handleSetAppControl_(payload) {
  var props = PropertiesService.getScriptProperties();
  var controlState = {
    appEnabled: !!payload.appEnabled,
    maintenanceMessage: String(payload.maintenanceMessage || ''),
    updatedAt: isoTimestamp_(),
    updatedBy: String(payload.updatedBy || 'GietErpAdmin')
  };

  props.setProperty('APP_CONTROL_ENABLED', String(controlState.appEnabled));
  props.setProperty('APP_CONTROL_MESSAGE', controlState.maintenanceMessage);
  props.setProperty('APP_CONTROL_UPDATED_AT', controlState.updatedAt);
  props.setProperty('APP_CONTROL_UPDATED_BY', controlState.updatedBy);

  writeControlFileToGitHub_(controlState);
  appendAuditLog_('set_app_control', JSON.stringify(controlState), controlState.updatedBy);

  return {
    success: true,
    message: 'App control updated.',
    appControl: controlState
  };
}

function getControlState_() {
  var props = PropertiesService.getScriptProperties();
  return {
    appEnabled: String(props.getProperty('APP_CONTROL_ENABLED') || 'true').toLowerCase() !== 'false',
    maintenanceMessage: props.getProperty('APP_CONTROL_MESSAGE') || '',
    updatedAt: props.getProperty('APP_CONTROL_UPDATED_AT') || '',
    updatedBy: props.getProperty('APP_CONTROL_UPDATED_BY') || ''
  };
}

function writeControlFileToGitHub_(controlState) {
  var props = PropertiesService.getScriptProperties();
  var token = props.getProperty('GITHUB_TOKEN');
  var owner = props.getProperty('GITHUB_OWNER');
  var repo = props.getProperty('GITHUB_REPO');
  var path = props.getProperty('GITHUB_CONTROL_PATH');
  var branch = props.getProperty('GITHUB_BRANCH') || 'main';

  if (!token || !owner || !repo || !path) {
    throw new Error('GitHub Script Properties are missing. Set GITHUB_TOKEN, GITHUB_OWNER, GITHUB_REPO, and GITHUB_CONTROL_PATH.');
  }

  var apiUrl = 'https://api.github.com/repos/' + owner + '/' + repo + '/contents/' + path + '?ref=' + branch;
  var headers = {
    Authorization: 'Bearer ' + token,
    Accept: 'application/vnd.github+json'
  };

  var currentResponse = UrlFetchApp.fetch(apiUrl, {
    method: 'get',
    headers: headers,
    muteHttpExceptions: true
  });

  if (currentResponse.getResponseCode() >= 400 && currentResponse.getResponseCode() !== 404) {
    throw new Error('Unable to fetch current GitHub control file: ' + currentResponse.getContentText());
  }

  var isCreatingFile = currentResponse.getResponseCode() === 404;
  var currentJson = isCreatingFile ? null : JSON.parse(currentResponse.getContentText());
  var content = Utilities.base64Encode(
    JSON.stringify(controlState, null, 2),
    Utilities.Charset.UTF_8
  );

  var updatePayload = {
    message: isCreatingFile ? 'Create app control state' : 'Update app control state',
    content: content,
    branch: branch
  };
  if (currentJson && currentJson.sha) {
    updatePayload.sha = currentJson.sha;
  }

  var updateResponse = UrlFetchApp.fetch('https://api.github.com/repos/' + owner + '/' + repo + '/contents/' + path, {
    method: 'put',
    headers: headers,
    contentType: 'application/json',
    payload: JSON.stringify(updatePayload),
    muteHttpExceptions: true
  });

  if (updateResponse.getResponseCode() >= 400) {
    throw new Error('Unable to update GitHub control file: ' + updateResponse.getContentText());
  }
}

function upsertUserRow_(payload) {
  var installId = String(payload.installId || '').trim();
  if (!installId) {
    throw new Error('Missing installId.');
  }

  var rollNo = normalizeRollNo_(payload.rollNo);
  var timestamp = String(payload.timestamp || isoTimestamp_());
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(USERS_SHEET);
  var rows = readRows_(sheet);
  var existingIndex = -1;

  rows.forEach(function(row, index) {
    if (String(row.installId || '') === installId) {
      existingIndex = index + 2;
    }
  });

  var existingFirstSeen = existingIndex > 0 ? sheet.getRange(existingIndex, 3).getValue() : '';
  var values = [
    installId,
    rollNo,
    existingFirstSeen || timestamp,
    timestamp,
    String(payload.appVersion || ''),
    String(payload.deviceModel || ''),
    String(payload.manufacturer || ''),
    String(payload.androidVersion || ''),
    String(payload.packageName || '')
  ];

  if (existingIndex > 0) {
    sheet.getRange(existingIndex, 1, 1, values.length).setValues([values]);
  } else {
    sheet.appendRow(values);
  }
}

function getBlockedRollMap_() {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(ROLL_BLOCKS_SHEET);
  var rows = readRows_(sheet);
  var map = {};
  rows.forEach(function(row) {
    var rollNo = normalizeRollNo_(row.rollNo);
    if (!rollNo) return;
    map[rollNo] = {
      isBlocked: String(row.isBlocked).toLowerCase() === 'true',
      blockMode: row.blockMode || 'details_only',
      note: row.note || ''
    };
  });
  return map;
}

function requireAdminSecret_(payload) {
  var expected = PropertiesService.getScriptProperties().getProperty('ADMIN_SECRET');
  if (!expected) {
    throw new Error('ADMIN_SECRET is not configured in Script Properties.');
  }

  if (String(payload.adminSecret || '') !== expected) {
    throw new Error('Invalid admin secret.');
  }
}

function ensureSheets_() {
  ensureSheet_(USERS_SHEET, USERS_HEADERS);
  ensureSheet_(ROLL_BLOCKS_SHEET, ROLL_BLOCK_HEADERS);
  ensureSheet_(AUDIT_LOG_SHEET, AUDIT_LOG_HEADERS);
}

function ensureSheet_(name, headers) {
  var spreadsheet = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = spreadsheet.getSheetByName(name);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(name);
  }

  if (sheet.getLastRow() === 0) {
    sheet.appendRow(headers);
  }
}

function readRows_(sheet) {
  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) {
    return [];
  }
  var headers = values[0];
  return values.slice(1).map(function(row) {
    var result = {};
    headers.forEach(function(header, index) {
      result[String(header)] = row[index];
    });
    return result;
  });
}

function appendAuditLog_(action, payloadSummary, updatedBy) {
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(AUDIT_LOG_SHEET);
  sheet.appendRow([action, payloadSummary, updatedBy, isoTimestamp_()]);
}

function normalizeRollNo_(rollNo) {
  return String(rollNo || '').trim().toUpperCase();
}

function summarizePayload_(payload) {
  return JSON.stringify({
    installId: payload.installId || '',
    rollNo: payload.rollNo || '',
    appVersion: payload.appVersion || ''
  });
}

function isoTimestamp_() {
  return Utilities.formatDate(new Date(), Session.getScriptTimeZone() || 'Asia/Kolkata', "yyyy-MM-dd'T'HH:mm:ssXXX");
}

function parsePayload_(e) {
  if (!e || !e.postData || !e.postData.contents) {
    return {};
  }
  return JSON.parse(e.postData.contents);
}

function jsonResponse(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
