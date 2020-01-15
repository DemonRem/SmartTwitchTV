// The bellow are some function or adptations of function from
// https://www.nightdev.com/kapchat/
function extraEmoticonize(message, emote) {
    return message.replace(emote.code, extraEmoteTemplate(emote));
}

function extraEmoteTemplate(emote) {
    return '<img class="emoticon" alt="" src="' + emote['4x'] + '"/>';
}

function emoteURL(id) {
    return 'https://static-cdn.jtvnw.net/emoticons/v1/' + id + '/3.0';//emotes 3.0 === 4.0
}

function emoteTemplate(url) {
    return '<img class="emoticon" alt="" src="' + url + '"/>';
}

function mescape(message) {
    return message.replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function extraMessageTokenize(message, chat_number, bits) {
    var tokenizedString = message.split(' '),
        emote,
        cheer;

    for (var i = 0; i < tokenizedString.length; i++) {
        message = tokenizedString[i];

        cheer = bits ? findCheerInToken(message, chat_number) : 0;

        if (cheer) {
            tokenizedString[i] = emoteTemplate(cheer);
            continue;
        }

        emote = extraEmotes[message.replace(/(^[~!@#$%\^&\*\(\)]+|[~!@#$%\^&\*\(\)]+$)/g, '')] || extraEmotes[message];

        tokenizedString[i] = emote ? extraEmoticonize(message, emote) : mescape(message);
    }

    return tokenizedString.join(' ') + (bits ? (' ' + bits + ' bits') : '');
}

function calculateColorReplacement(color) {
    // Modified from http://www.sitepoint.com/javascript-generate-lighter-darker-color/
    var rgb = "#",
        brightness = "0.5", c, i;

    if (color === '#000000') return "#2cffa2";//Black can't be see on a black background

    color = String(color).replace(/[^0-9a-f]/gi, '');
    if (color.length < 6) {
        color = color[0] + color[0] + color[1] + color[1] + color[2] + color[2];
    }

    for (i = 0; i < 3; i++) {
        c = parseInt(color.substr(i * 2, 2), 16);
        if (c < 10) c = 10;
        c = Math.round(Math.min(Math.max(0, c + (c * brightness)), 255)).toString(16);
        rgb += ("00" + c).substr(c.length);
    }

    return rgb;
}

function findCheerInToken(message, chat_number) {
    var cheerPrefixes = Object.keys(cheers[ChatLive_selectedChannel_id[chat_number]]),
        tokenLower = message.toLowerCase(),
        index = -1;


    for (var i = 0; i < cheerPrefixes.length; i++) {
        //Try  case sensitive first as some prefixes start the same, but some users type without carrying about case
        if (message.startsWith(cheerPrefixes[i]))
            return getCheer(cheerPrefixes[i], parseInt(message.substr(cheerPrefixes[i].length), 10), chat_number);

        //Try  case insensitive after
        if (tokenLower.startsWith(cheerPrefixes[i].toLowerCase())) index = i;
    }

    return ((index > -1) ?
        getCheer(cheerPrefixes[index], parseInt(tokenLower.substr(cheerPrefixes[index].toLowerCase().length), 10), chat_number)
        : null);
}

function getCheer(prefix, amount, chat_number) {
    var amounts = cheers[ChatLive_selectedChannel_id[chat_number]][prefix],
        amountsArray = Object.keys(amounts),
        length = amountsArray.length;

    //Run on reverse order to catch the correct position amountsArray = 1000, 500, 100, 1 ... amount = 250
    while (length--) {
        if (amount >= amountsArray[length]) return amounts[amountsArray[length]];
    }

    //Fail safe
    return amounts[amountsArray[0]];
}

function emoticonize(message, emotes) {
    if (!emotes) return [message];

    var tokenizedMessage = [];

    var emotesList = Object.keys(emotes);

    var replacements = [];

    emotesList.forEach(function(id) {
        var emote = emotes[id];

        for (var i = emote.length - 1; i >= 0; i--) {
            replacements.push({
                id: id,
                first: emote[i][0],
                last: emote[i][1]
            });
        }
    });

    replacements.sort(function(a, b) {
        return b.first - a.first;
    });

    // Tokenizes each character into an array
    // punycode deals with unicode symbols on surrogate pairs
    // punycode is used in the replacements loop below as well
    message = punycode.ucs2.decode(message);

    replacements.forEach(function(replacement) {
        // Unshift the end of the message (that doesn't contain the emote)
        tokenizedMessage.unshift(punycode.ucs2.encode(message.slice(replacement.last + 1)));

        // Unshift the emote HTML (but not as a string to allow us to process links and escape html still)
        tokenizedMessage.unshift([emoteTemplate(emoteURL(replacement.id))]);

        // Splice the unparsed piece of the message
        message = message.slice(0, replacement.first);
    });

    // Unshift the remaining part of the message (that contains no emotes)
    tokenizedMessage.unshift(punycode.ucs2.encode(message));

    return tokenizedMessage;
}

function transformBadges(sets) {
    return Object.keys(sets).map(function(b) {
        var badge = sets[b];
        badge.type = b;
        badge.versions = Object.keys(sets[b].versions).map(function(v) {
            var version = sets[b].versions[v];
            version.type = v;
            return version;
        });
        return badge;
    });
}

function tagCSS(type, version, url, doc) {
    var style = document.createElement('style');
    style.type = 'text/css';
    style.innerHTML = '.' + type + '-' + version + ' { background-image: url("' + url.replace('http:', 'https:') + '"); }';
    if (doc) doc.appendChild(style);
    else document.head.appendChild(style);
}