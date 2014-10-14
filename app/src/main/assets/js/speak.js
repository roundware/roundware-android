$(document).ready(function() {

    // call Roundware.speak_main with a callback to call when
    // initialization is complete. the callback will be passed
    // an array of category names
    Roundware.speak_main(speak_callback);

    var done = false;

    function speak_callback(tag_list, selected_tags)
    {
        var $container = $('#container');

        $container.isotope({
            itemSelector: '.element',
            columnWidth: function( containerWidth ) {
                return containerWidth / 6;
            },
            isFitWidth: true,
            isAnimated: !Modernizr.csstransitions,
        });

        // show the first tag category
        $container.isotope({ filter: '.' + tag_list[0] });

        $('#nav a').click(function(){
            var selector = $(this).attr('data-filter');
            $container.isotope({ filter: selector });
            console.log(selector);
            return false;
        });

        $('#cancel').tappable(function(){
            window.location = "roundware://speak_cancel";
            console.log('roundware://speak_cancel');
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

                      // clear selected tags
                      Roundware.selected_tags_by_category[Roundware.tags.speak[slider_pos].code] = [];

                      if (tagbox.hasClass('selected')) {
                          $.each(Roundware.selected_tags_by_category[item.code], function(t, tag) {
                              if (tag == tag_id) {
                                  Roundware.selected_tags_by_category[item.code].splice(t,1);
                                  return false;
                              }
                          });
                      }
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

                      if (Roundware.tags.speak[i + 1]) {
                          $container.isotope({ filter: '.' + tag_list[i + 1] });
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

                if (tag_list[pos]) {
                    $('#container').isotope({ filter: '.' + tag_list[pos] });
                    slider_pos = pos;
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

