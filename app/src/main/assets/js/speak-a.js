$(function() {

    // call Roundware.speak_main with a callback to call when
    // initialization is complete. the callback will be passed
    // an array of category names
    Roundware.speak_main(speak_callback);

    var done = false;

    function speak_callback(tag_list, selected_tags)
    {
        var current_filters = '';
        var $container = $('#container');

        $container.isotope({
            itemSelector: '.element',
            resizable: false, // enable/disable normal resizing
            columnWidth: function( containerWidth ) {
                return containerWidth / 6;
            },
            onLayout: function() {
                // var upper_height = $('#gallery').height() + $('#nav').height();
                // remaining_height = parseInt(($(window).height() - upper_height), 10);
                // $('#scroller').height(remaining_height);
                // $('#scroller').height(remaining_height - 11);
                console.log("onlayout");
            },
            isFitWidth: true,
            isAnimated: !Modernizr.csstransitions
        });

        // show the first tag category
        $container.isotope({ filter: '.' + tag_list[0] });

        $('#nav a').click(function(){
            console.log('clicked a nav a element');
            var selector = $(this).attr('data-filter');
            $container.isotope({ filter: selector });
            var upper_height = $('#gallery').height() + $('#nav').height();
            remaining_height = parseInt(($(window).height() - upper_height), 10);
            $('#scroller').height(remaining_height);
            console.log(selector);
            return false;
        });

        $('#cancel').tappable(function(){
            window.location = "roundware://speak_cancel";
            console.log('clicked cancel');
            reset_speak();
            return false;
        });

        // add click-handlers for each element
        //
        // if unselected element is tapped, it becomes .selected and
        // all other elements that are visible (not .isotope-hidden) become .unselected
        $.each(Roundware.tags.speak, function(i, item) {

            $('#selections').append('<li id="' + item.code +'-selected">'+ item.code +'</li>');

            $('.' + item.code).tappable({
                callback:
                    function() {
                        done = false;
                        var tagbox = $(this);
                        var tag_id = $(this).data('tag_id');

                        console.log('the tag_id is ' + tag_id);
                        console.log('the item code is ' + item.code);

                        // clear selected tags
                        Roundware.selected_tags_by_category[Roundware.tags.speak[slider_pos].code] = [];

                        // if the tag is already selected, turn it off
                        if (tagbox.hasClass('selected')) {
                            $.each(Roundware.selected_tags_by_category[item.code], function(t, tag) {
                                if (tag == tag_id) {
                                    Roundware.selected_tags_by_category[item.code].splice(t,1);
                                    return false;
                                }
                            });
                        }

                        // if the tag is not selected, turn it on
                        else if (tagbox.hasClass('unselected')) {
                            selected_tags.push($(this).data('tag_id'));
                            $(".isotope-item").not(".isotope-hidden").removeClass('selected').addClass('unselected');
                            tagbox.removeClass('unselected');
                            tagbox.addClass('selected');
                            Roundware.selected_tags_by_category[item.code].push(tag_id);
                        }
                        else {
                            return false;
                        }

                        // do we have another tag-category to show, or are we done?
                        if (Roundware.tags.speak[i + 1]) {
                            current_filters = '.t' + tag_id + current_filters;
                            console.log("filters= " + current_filters + '.' + tag_list[i + 1]);
                            $container.isotope({ filter: current_filters + '.' + tag_list[i + 1] });
                            $("#header").text(Roundware.tags.speak[i + 1].name);
                            slider.next();
                            $("#" + item.code + "-selected").text(tagbox.text());
                        }
                        else {
                            done = true;
                        }

                        // send data to the host app
                        do_the_magic(done);
                    },
                    // deactivate after all categories have had selection made
                    onlyIf:   function() { if(done === false) {return true;} else { return false;} }

            });
        });

        // log tag selections
        function do_the_magic(done) {
            var cat_list = [];
            $.each(Roundware.selected_tags_by_category, function(key, val) {
                cat_list.push(key + '=' + val.join(','));
            });
            cat_list.push('done=' + (done ? 'true' : 'false'));
            window.location = "roundware://project?" + cat_list.join('&');
            console.log("roundware://project?" + cat_list.join('&'));
            if (done === true) {
                reset_speak();
            }
        }

        function reset_speak() {
            window.setTimeout(function() {
                $('.element').removeClass('selected').addClass('unselected');
                slider.slide(0,500);
            }, 2000 );
            done = false;
        }

        // slider code
        // var slider_pos = 0;
        var slider = new Swipe(document.getElementById('slider'), {
            callback: function(e, pos) {

                var i = bullets.length;
                while (i--) {
                    bullets[i].className = ' ';
                }
                bullets[pos].className = 'on';

                // reset filters if first tag category is reached
                // ultimately this should dynamically undo the filter stack whenever 
                // previous tag categories / slider positions are accessed
                // for now, I'm hard-coding it to reset at the primary exhibit category
                if (pos === 0) {
                    current_filters = '';
                }
                if (pos === 2) {
                    var upper_height = $('#gallery').height() + $('#nav').height();
                    remaining_height = parseInt(($(window).height() - upper_height), 10);
                    $('#scroller').height(remaining_height + 1);
                }

                if (tag_list[pos]) {
                    //$('#container').isotope({ filter: '.' + tag_list[pos] });
                    $container.isotope({ filter: current_filters + '.' + tag_list[pos] });
                    slider_pos = pos;
                    if (slider_pos === 0) {
                        iso_filter = '.' + tag_list[slider_pos];
                        $('#container').isotope({ filter: iso_filter });
                    }
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

    }
});
