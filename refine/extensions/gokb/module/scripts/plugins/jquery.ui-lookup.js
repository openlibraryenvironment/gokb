/*!
 * jQuery UI Lookup 1.2.0
 *
 * Copyright 2011, Simon Bartlett
 * Licensed under the MIT
 *
 * https://github.com/SimonBartlett/jquery-ui-lookup
 */

(function ($) {

	// Compares a version string to the jQuery UI'sversion string
	// Compares to the 
	function compareUiVersion(version) {
		var versions = $.ui.version.split('.');
		var params = version.split('.');
		var depth = params.length;

		for (var i = 0; i < depth; i++) {
			if (versions[i]) {
				var v = 1 * versions[i];
				var p = 1 * params[i];

				if (v > p) {
					return -1;
				}

				if (v < p) {
					return 1;
				}
			}
		}

		return 0;
	}

	$.lookup = {

		createBodyTemplate: function (name) {
			return '<div id="' + name + '" style="display:none;">' +
				'<div style="height: 30px;"><label for="' + name + '_search" style="display: inline;">Search: </label><input id="' + name + '_search"  type="text" class="ui-widget-content ui-corner-all" style="padding: 2px;" /></div>' +
				'<div class="ui-lookup-results"></div>' +
				'</div>';
		}

	};

	$.widget('ui.lookup', {
		version: '1.2.0',
		options: {
			cancelText: 'Cancel',
			delay: 300,
			disabled: false,
			height: 400,
			minLength: 1,
			modal: true,
			name: null,
			okText: 'Ok',
			renderItem: null,
			resizable: false,
			select: null,
			source: null,
			title: 'Search',
			value: null,
			width: 600
		},
		_open: null,
		_resizeAutocomplete: function () {
			var context = $(this).is('.ui-dialog-content') ? $(this) : $(this).parent().parent();
			$('.ui-lookup-results ul', context).css({
				top: '35px',
				left: '9px',
				overflowY: 'auto',
				overflowX: 'hidden',
				width: $('.ui-lookup-results', context).width(),
				height: context.height() -  $('div:first', context).outerHeight()
			});
		},
		_create: function () {

			if (this.options.name === null) {
				this.options.name = 'dialog_' + Math.floor(Math.random() * 10001).toString();
			}

			var $this = this,
				dialogBody = $.lookup.createBodyTemplate($this.options.name),
				buttons = { };

			buttons[$this.options.okText] = function () {
				if($this.options._value && $this.options.value != $this.options._value) {
					$this.options.value = $this.options._value;
					if ($this.options.select) {
						$this.options.select($this.options.value);
					}
				}
				$(this).dialog('close');
			};
			buttons[$this.options.cancelText] = function () {
				$(this).dialog('close');
			};

			$this._dialog = $(dialogBody).dialog({
				autoOpen: false,
				height: $this.options.height,
				modal: true,
				resizable: $this.options.resizable,
				title: $this.options.title,
				width: $this.options.width,
				buttons: buttons,
				resize: $this._resizeAutocomplete,
				open: function (dialogEvent, dialogUi) {
					$this._autocomplete = $('input', $this._dialog)
						.autocomplete({
							appendTo: '#' + $this.options.name + ' .ui-lookup-results',
							delay: $this.options.delay,
							minLength: $this.options.minLength,
							source: $this.options.source,
							open: $this._resizeAutocomplete,
							select: function (event, ui) {
								var selected = $('#ui-active-menuitem');
								setTimeout(function () {
									$this._dialog.find('.ui-lookup-results ul li a').removeClass('ui-state-active');
									selected.addClass('ui-state-active');
								}, 10);
								$this.options._value = ui.item;
								return false;
							},
							focus: function (event, ui) {
								var focused = $('#ui-active-menuitem');
								if (!focused.hasClass('ui-state-active')) {
									focused.addClass('ui-state-hover');
								}
								return false;
							}
						})
						.focus();
					
					var self = $this._autocomplete.data('autocomplete');
					self.close = function () {};

					if (compareUiVersion('1.8.20') == 1) {
						self._response = function (content) {
							if (!self.options.disabled && content) {
								content = self._normalize(content);
								self._suggest(content);
								self._trigger('open');
							} else {
								self.close();
							}
							self.pending--;
							if (!self.pending) {
								self.element.removeClass( "ui-autocomplete-loading" );
							}
						};
					}


					self.menu.element.dblclick(function (event) {
						if (!$(event.target).closest('.ui-menu-item a').length) {
							return;
						}
						event.preventDefault();
						self.menu.select(event);
						$this.options.value = $this.options._value;
						if ($this.options.select) {
							$this.options.select($this.options.value);
						}
						$this._dialog.dialog('close');
					});
					if($this.options.renderItem) {
						self._renderItem = $this.options.renderItem;
					}
				}
			});
			
			$this._open = function () {
				if (!$this.options.disabled) {
					$this._dialog.dialog('open');
				}
			}

			$this.element.bind('focus', $this._open);
			$this.element.bind('click', $this._open);
		},
		value: function () {
			return this.options.value;
		},
		destroy: function () {
			this.element.bind('focus', this._open);
			this.element.bind('click', this._open);
			this._autocomplete.autocomplete('destroy');
			this._dialog.dialog('destroy');
			$.Widget.prototype.destroy.apply(this, arguments);
		},
		disable: function () {
			this.options.disabled = true;
		},
		enable: function () {
			this.options.disabled = false;
		}
	});

}(jQuery));