$(document).ready(function() {

	// call Roundware.listen_main with a project-id and the name of
	// function to call when initialization is complete.
	// the callback will be passed an array of category names
	Roundware.listen_main(listen_callback);

	function listen_callback(tag_list, selected_tags)
	{
        var $container = $('#container');

		$container.isotope({
			itemSelector: '.element',
			resizable: false, // disable normal resizing
			masonry: {
				columnWidth: $container.width() / 6,
				// gutterWidth: 50,
			},
			isFitWidth: true,
			isAnimated: !Modernizr.csstransitions,
		});

		// show the
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

			$('.' + item).tappable(
				function() {
					var tagbox = $(this);
					var tag_id = $(this).data('tag_id');
					//num_selected = $
					if (tagbox.hasClass('tag')) {
						// do not allow usertype tags to be toggled other than by slider
						if (tagbox.hasClass('usertype')) {
							return;
						}
						tagbox.removeClass('tag');
						tagbox.removeClass('selected');
						tagbox.addClass('unselected');
						num_selected = $('.' + item + '.selected').length;
						console.log("number selected: " + num_selected + " of " + num_on_display);

						$.each(Roundware.selected_tags_by_category[item], function(t, tag) {
							if (tag == tag_id) {
								Roundware.selected_tags_by_category[item].splice(t,1);
								return false;
							}
						});
						if (num_selected == 0) {
								$(".isotope-item").not(".isotope-hidden").removeClass('unselected').addClass('selected').addClass('tag');
								// clear selected tags ...
								Roundware.selected_tags_by_category[Roundware.tags.listen[slider_pos].code] = [];
								// and turn them all back on
 								$.each(Roundware.tags.listen[slider_pos].options, function(t, tag) {
 									Roundware.selected_tags_by_category[Roundware.tags.listen[slider_pos].code].push(tag.tag_id);
 								});
						}

						//return false; // will stop annoying popups in mobile safari, but prevents webview message entirely
					}
					else {
						/*$(this).css({backgroundColor: '#c0220d'});*/
						tagbox.addClass('tag');
						tagbox.addClass('selected');
						tagbox.removeClass('unselected');
						num_selected = $('.' + item + '.selected').length;
						console.log("number selected: " + num_selected + " of " + num_on_display);

						Roundware.selected_tags_by_category[item].push( tag_id );
						//return false; // will stop annoying popups in mobile safari, but prevents webview message entirely
					}

					// log tag selections
					do_the_magic();
				}

			);
		});

		function select_all() {
			$(".isotope-item").not(".isotope-hidden").removeClass('unselected').addClass('selected').addClass('tag');

			// clear selected tags ...
			Roundware.selected_tags_by_category[Roundware.tags.listen[slider_pos].code] = [];

			// and turn them all back on
			$.each(Roundware.tags.listen[slider_pos].options, function(t, tag) {
				Roundware.selected_tags_by_category[Roundware.tags.listen[slider_pos].code].push(tag.tag_id);
			});
			do_the_magic();
		}

		// log tag selections
		function do_the_magic() {
			var cat_list = [];
			$.each(Roundware.selected_tags_by_category, function(key, val) {
				cat_list.push(key + '=' + val.join(','));
			});
			window.location = "roundware://project?" + cat_list.join('&');
			console.log("roundware://project?" + cat_list.join('&'));
		}

		// update columnWidth on window resize
		$(window).smartresize(function(){
			$container.isotope({
				// update columnWidth to a percentage of container width
				masonry: { columnWidth: $container.width() / 6 }
			});
		});


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
				if (tag_list[pos] === 'usertype') {
					$('#usertype-slider').fadeIn(500);
					$('#select-all').fadeOut(200);
				}
				if (tag_list[pos] != 'usertype') {
					$('#usertype-slider').fadeOut(500);
					$('#select-all').css('visibility', 'inherit');
					$('#select-all').fadeIn(200);
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

		var uip = 0.5;

		if (($(".museum").hasClass("selected") && $(".community").hasClass("selected"))) {
			uip = .5;
		}
		else if ($(".museum").hasClass("unselected") && $(".community").hasClass("selected")) {
			uip = 0;
		}
		else if ($(".museum").hasClass("selected") && $(".community").hasClass("unselected")) {
			uip = 1;
		}

		$( "#usertype-slider" ).slider({
	        value: uip,
	        min: 0,
	        max: 1,
	        step: .01,
	        // animate: "fast",
	        slide: function( event, ui ) {
		        $( "#amount" ).val( "$" + ui.value );
		        $('.museum').css('opacity', ui.value * 2);
		        $('.community').css('opacity', (2 - (ui.value * 2)));
		        if (ui.value >= .6) {
		        	$('.community').removeClass('selected').addClass('unselected');
		    		$('.museum').removeClass('unselected').addClass('selected');
		    	}
		    	else if (ui.value < .6 && ui.value > .4) {
		    		$('.community').removeClass('unselected').addClass('selected');
		    		$('.museum').removeClass('unselected').addClass('selected');
		    	}
		    	else if (ui.value <= .4) {
		    		$('.community').removeClass('unselected').addClass('selected');
		   			$('.museum').removeClass('selected').addClass('unselected');
		    	}
		    	// total hack for sending proper tag ids to host app when slider crosses threshold values
		    	if (ui.value === 0) {
		    		var tag_id = $('.community').data('tag_id');
					console.log("tag_id = " + tag_id);
					Roundware.selected_tags_by_category['usertype'].splice(0,2,tag_id);
		    		do_the_magic();
		    	} else if (ui.value === .4) {
		    		var tag_id = $('.museum').data('tag_id');
					console.log("tag_id = " + tag_id);
	    			Roundware.selected_tags_by_category['usertype'].splice(0,2,tag_id);
	    			var tag_id = $('.community').data('tag_id');
	    			Roundware.selected_tags_by_category['usertype'].splice(0,0,tag_id);
		    		do_the_magic();
		    	} else if (ui.value === .6) {
		    		var tag_id = $('.museum').data('tag_id');
					console.log("tag_id = " + tag_id);
	    			Roundware.selected_tags_by_category['usertype'].splice(0,2,tag_id);
	    			var tag_id = $('.community').data('tag_id');
	    			Roundware.selected_tags_by_category['usertype'].splice(0,0,tag_id);
		    		do_the_magic();
		    	} else if (ui.value === 1) {
					var tag_id = $('.museum').data('tag_id');
					console.log("tag_id = " + tag_id);
	    			Roundware.selected_tags_by_category['usertype'].splice(0,2,tag_id);
		    		do_the_magic();
		    	}
	        }
	    });
	}

});


// iScroll code
document.addEventListener('touchmove', function (e) { e.preventDefault(); }, false);
