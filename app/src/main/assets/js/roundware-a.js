if (!console) console = {};

if (typeof console.log != "function") {
    console.log = function() { /*placeholder for IE */ };
}

var Roundware = {

    // json string as returned by a get_tags operation from a roundware server
    tags : {},

    // array of tag_id's of selected tags
    selected_tags : [],

    selected_tags_by_category : {},


    /**
     * listen handler
     */
    listen_main : function(callback) {
        var ignore_defaults = false;
        Roundware.generate_buttons(Roundware.tags.listen, ignore_defaults, callback);
    },



    /**
     * speak handler
     */
    speak_main : function(callback) {
        var ignore_defaults = true;
        Roundware.generate_buttons(Roundware.tags.speak, ignore_defaults, callback);
    },



    /**
     * parse the given JSON data
     *
     * @param JSON data: string of tag data from the RW server
     * @param function callback: function to call when parsing is complete
     */
    generate_buttons : function(data, ignore_defaults, callback) {
        var id = ignore_defaults;
//      $('#container').append(Roundware.parse_tags(data));
        Roundware.parse_tags('#container', id, data);

        // sort tag categories by order parameter
        data.sort(function(a,b) {
            if (a.order > b.order) {
                return 1;
            }
            else if (a.order < b.order) {
                return -1;
            }
            else {
                return 0;
            }
        });

        var tag_list = [];

        $.each(data, function(i, item) {
            tag_list.push(this.code);
            var display = (i === 0) ? 'block' : 'none';
            $('#slider ul').append('<li style="display: ' + display + '"><div>' + this.name + '<br></div><div class="instruction">Select from the options below</div></li>');

            display = (i === 0) ? ' class="on"' : '';
            $('#position').append('<em' + display + '>&bull;</em>');

        });

        callback(tag_list, Roundware.selected_tags);

    },



    /**
     * Given a list of tags as a JSON array, convert them into an HTML string and return them.
     */
    parse_tags : function(append_to, id, data) {
        $.each(data, function(i, item) {
            Roundware.show_multi(append_to, id, item);
        });

    },



    /**
     * Given a JSON object representing a select-multi item, convert it to a string of
     * HTML checkboxes and return it.
     *
     * @param field
     * @returns {String}
     */
    show_multi : function(append_to, id, field) {
        var str = '';
        console.log(id);
        Roundware.selected_tags_by_category[field.code] = [];

        // sort tags by order parameter
        field.options.sort(function(a,b) {
            if (a.order > b.order) {
                return 1;
            }
            else if (a.order < b.order) {
                return -1;
            }
            else {
                return 0;
            }
        });

        $.each(field.options, function(i, item) {
            var checked = ' unselected ';
            if(id === false) {
                $.each(field.defaults, function(j, field_default) {
                  if (field_default == item.tag_id) {
                      Roundware.selected_tags.push(field_default);
                      checked = ' selected tag ';
                      Roundware.selected_tags_by_category[field.code].push(item.tag_id);
                  }
              });
              console.log("defaults not ignored! LISTEN");
            }

            // prefix relationship tag_ids with 't' so they can be used as CSS classnames
            var relationships = item.relationships.map(function(r) { return 't'+r;}).join(' ');

            var splitString = item.value.split('|');
            // var splitString = item.value.split('%^&');
            var newString = '';
            if (splitString.length > 1) {
                newString = "<strong>" + splitString[0] + "|</strong> " + splitString[1];
            } else {
                newString = splitString[0];
            }

            var tag = $('<div id="t'+ item.tag_id+ '" class="element ' + field.code + checked + relationships + '"><p>' + newString + '</p><div class="checkmark"></div></div>');
            tag.data('tag_id', item.tag_id);

            // stash tag's data attributes as name-value pairs on the jQuery item
            $.each(item.data.split(","), function () {
                var pair = this.split("=");
                tag.data(pair[0], pair[1]);
            });

            // append a CSS class to this element, or the class onebyone if no class was specified
            tag.addClass(tag.data('class') || 'onebyone');

            // finally, append this element to the DOM
            tag.appendTo(append_to);

        });

    },



    /**
     * Given a JSON object representing a select-multi item, convert it to a string of
     * HTML checkboxes and return it.
     *
     * @param field
     * @returns {String}
     */
    show_one_or_all : function(field) {
        var str = '';
    //  return str;

    }

};
