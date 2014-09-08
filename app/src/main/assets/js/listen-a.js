$(function() {

    // call Roundware.listen_main with a project-id and the name of
    // function to call when initialization is complete.
    // the callback will be passed an array of category names
    Roundware.listen_main(listen_callback);

    function listen_callback(tag_list, selected_tags)
    {
        var current_filters = [];
        var $container = $('#container');

        $container.isotope({
            itemSelector: '.element',
            resizable: false, // disable normal resizing
            //resizesContainer: false, // makes no isotope tiles displa for some reason
            masonry: {
                columnWidth: $container.width() / 6
                // gutterWidth: 50,
            },
            onLayout: function() {
                // var upper_height = $('#gallery').height() + $('#nav').height();
                // remaining_height = parseInt(($(window).height() - upper_height), 10);
                // $('#scroller').height(remaining_height);
                // console.log("onlayout");
            },
            isFitWidth: true,
            isAnimated: !Modernizr.csstransitions
        });

        // show the first tag category's items
        $container.isotope({ filter: '.' + tag_list[0] });

        $('#nav a').click(function(){
            var selector = $(this).attr('data-filter');
            $container.isotope({ filter: selector });
            return false;
        });

        $('#select-all').tappable(function(){
            select_all();
            return false;
        });

        $('#done').tappable(function(){
            window.location = "roundware://listen_done";
            console.log('clicked done');
            return false;
        });

        // add click-handlers for each element
        //
        // if unselected element is tapped, it becomes .selected and vice-versa
        // if the last selected element is tapped, all elements are turned on in order to preserve multi_at_least_one functionality
        $.each(tag_list, function(i, item) {
            var num_selected = $('.' + item + '.selected').length;
            var num_on_display = $(".isotope-item").not(".isotope-hidden").size();
//          console.log('item is ' + item);
            $('.' + item).tappable(
                function() {
                    var tagbox = $(this);
                    var tag_id = $(this).data('tag_id');
                    //console.log('this is ' + tagbox.html());
                    //num_selected = $

                    // reset selections of all downstream tags when tag is selected or unselected.
                    // otherwise, a previous selection's filters could be applied to this
                    // new selection and might erroneously hide things.
                    rc = slider_pos + 1; // increment slider_pos in order to not reset current category
                    for (rc; rc < tag_list.length; rc++) {
                        console.log("resetting: " + tag_list[rc]);
                        select_all_category("." + tag_list[rc]);
                    }
                    // tags with 'single' class are single select
                    if (tagbox.hasClass('single')) {
                        if (tagbox.hasClass('unselected')) {
                            console.log("unselected clicked");
                            tagbox.removeClass('unselected');
                            tagbox.addClass('selected');
                            $(".isotope-item").not(".isotope-hidden").not(this).removeClass('selected').addClass('unselected');
                            num_selected = $('.' + item + '.selected').length;
                            // ZB need to loop through all subsequent tag categories, selecting all in each of them
                            // this is a hard-coded proof of concept only
                            // select_all_category(".question");
                            // select_all_category(".demographic");
                            //return false; // will stop annoying popups in mobile safari, but prevents webview message entirely
                        }
                        else {
                            // tag stays selected if tapped when already selected
                            console.log("already selected!");
                        }
                    }

                    else {
                        // turn a tag OFF
                        if (tagbox.hasClass('selected')) {
                            tagbox.removeClass('selected');
                            tagbox.addClass('unselected');
                            num_selected = $('.' + item + '.selected').not(".isotope-hidden").length;
                            console.log(num_selected);
                            // if nothing is selected, turn everything back on.
                            if (num_selected === 0) {
                                $(".isotope-item").not(".isotope-hidden").removeClass('unselected').addClass('selected');
                            }
                            //return false; // will stop annoying popups in mobile safari, but prevents webview message entirely
                        }
                        // turn a tag ON
                        else {
                            tagbox.addClass('selected');
                            tagbox.removeClass('unselected');
                            num_selected = $('.' + item + '.selected').length;
                            //return false; // will stop annoying popups in mobile safari, but prevents webview message entirely
                        }
                    }

                    //current_filters = '.t' + tag_id + current_filters;
                    //$container.isotope({ filter: current_filters + '.' + tag_list[i + 1] });

                    // log tag selections
                    do_the_magic();
                }

            );
        });

        function select_all() {
            $(".isotope-item").not(".isotope-hidden").removeClass('unselected').addClass('selected').addClass('tag');
            do_the_magic();
        }

        function select_all_category(cat) {
            $(".isotope-item" + cat).removeClass('unselected').addClass('selected').addClass('tag');
            //do_the_magic();
        }

        /**
         * build a hash mapping tag-categories to an array of IDs for selected tags within
         * the category, e.g. { exhibit : [1,2,3], demographic : [4,5,6]}.
         */
        function selection_hash() {
            var hash = {};
            $.each(tag_list, function(i, category) {
                hash[category] = [];
                $.each($('.selected.' + category + '.isotope-item'), function(j, tag){
                    hash[category].push($(tag).data('tag_id'));
                });
            });

            return hash;
        }


        /**
         * log tag selections
         * loop through the hash of category => selected-tags and make sure all the
         * selected tags are actually available given the selections higher up in
         * the category tree. this is important because the default selection may be
         * for every tag to be selected, but as top-level categories are turned off,
         * tags in lower categories may become unavailable and therefore should be
         * filtered out.
         */
        function do_the_magic() {
            // a hash mapping category names to selected tags within that category
            var hash = selection_hash();

            // array of flattened category selections, e.g. [ 'cat-a=1,2,3', 'cat-b=5,6,7']
            var cat_list = [];

            // loop over categories, then selected tags within each category.
            // in the inner loop, make sure a (possibly default) selection is actually
            // valid by comparing pulling it through the filter that would be applied
            // to that category's tags.
            $.each(hash, function(category, items) {

                // get the filter to apply to this category
                var filter = filter_builder(category, hash);

                // build array of actually available tags from the list of selected tags
                var available = [];
                $.each(items, function(i, item) {
                    if ($('#t' + item).is(filter)) {
                        available.push(item);
                    }
//                  console.log(category + '/' + item + ' is ' + (available ? '' : 'not ') + 'available');
                });

                // flatten list of selected tags for this category
                cat_list.push(category + '=' + available.join(','));

            });

            window.location = "roundware://project?" + cat_list.join('&');
            console.log("roundware://project?" + cat_list.join('&'));
        }


        /**
         * loop through the tag selections, building up pairwise filters. e.g. if we have
         * category [item] lists:
         * colors [red green blue]
         * shapes [square circle triangle]
         * patterns [stripes spots]
         * build up a list like [red-square-stripes, red-square-spots, red-circle...
         * ... blue-triangle-stripes, blue-triangle-spots]
         * that includes every possible tuple.
         *
         * if tag_list is simple, e.g. [ colors: [red, blue], shapes: [circle, square]]
         * and selector is colors, then this will generate a filter like
         * ".red.circle.colors, .red.square.colors, .blue.circle.colors, .blue.square.colors"
         *
         * @param selector the category to apply the filter to
         * @param hash a hash mapping category names to selected tags within that category
         */
        function filter_builder(selector, hash)
        {
            if (! hash) hash = selection_hash();
            var the_list = [];
            $.each(tag_list, function(i, category){
                var list_copy = [];
                $.each(hash[category], function(k, tag){
                    if (the_list.length) {
                        $.each(the_list, function(i, item) {
                            list_copy.push('.t' + tag + item);
                        });
                    }
                    else {
                        if (selector) {
                            list_copy.push('.t' + tag + '.' + selector);
                        }
                        else {
                            list_copy.push('.t' + tag);
                        }
                    }
                });

                the_list = list_copy;
            });

            //console.log(the_list);
            return the_list.join(',');
        }


        // update columnWidth on window resize
        $(window).smartresize(function(){
            $container.isotope({
                // update columnWidth to a percentage of container width
                masonry: { columnWidth: $container.width() / 6 }
            });
        });


        // slider code
        var slider_pos = 0;
        var slider = new Swipe(document.getElementById('slider'), {
            callback: function(e, pos) {
                var i = bullets.length;
                while (i--) {
                    bullets[i].className = ' ';
                }
                bullets[pos].className = 'on';

                if (tag_list[pos]) {
                    slider_pos = pos;
                    console.log("slider pos = " + slider_pos);
                    // var num_on_display = $(".isotope-item").not(".isotope-hidden").size();

                    $('#container').isotope({ filter: filter_builder(tag_list[slider_pos]) });
                    //console.log("slider = " + slider_pos);
                    // console.log("filter = " + filter_builder(tag_list[slider_pos]));
                    // console.log("tag_list = " + tag_list[slider_pos]);
                    // console.log("tag_list = " + slider_pos);
                    // hack to get isotope container to resize and scroll properly without cutting off options
                    if (pos === 2) {
                        var upper_height = $('#gallery').height() + $('#nav').height();
                        remaining_height = parseInt(($(window).height() - upper_height), 10);
                        setTimeout(function(){$('#scroller').height(remaining_height+2);},1000);
                    }
                    // do not show Select All button for Exhibit category
                    if (slider_pos === 0) {
                        $('#select-all').hide();
                        // ensure that ALL Exhibits are displayed regardless of other selected tag relationships
                        console.log("tag_list0 = " + tag_list[slider_pos]);
                        iso_filter = '.' + tag_list[slider_pos];
                        console.log("iso_filter = " + iso_filter);
                        $('#container').isotope({ filter: iso_filter });
                        var upper_height = $('#gallery').height() + $('#nav').height();
                        remaining_height = parseInt(($(window).height() - upper_height), 10);
                        setTimeout(function(){$('#scroller').height(remaining_height);},1000);
                    }
                    else if (slider_pos !== 0) {
                        $('#select-all').show();
                    }
                    do_the_magic();
                    }

                else {
                    return false;
                }

            }
        }),
        bullets = document.getElementById('position').getElementsByTagName('em');

        // click handlers for the previous and next buttons
        $('#prev').on('click', function() { slider.prev(); return false;});
        $('#next').on('click', function() { slider.next(); return false;});

        /*$('#select-all').on('click', function(event) {
            select_all();
            console.log('clicked select all');
            return false;
        });*/


    }

});


// iScroll code
document.addEventListener('touchmove', function (e) { e.preventDefault(); }, false);
