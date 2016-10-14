$(function() {
    $('#login-form-link').click(function(e) {
    	$("#login-form").delay(500).fadeIn(500);
 		$("#register-form").fadeOut(500);
		$('#register-form-link').removeClass('active');
		$(this).addClass('active');
		e.preventDefault();
	});
	$('#register-form-link').click(function(e) {
		$("#register-form").delay(500).fadeIn(500);
 		$("#login-form").fadeOut(500);
		$('#login-form-link').removeClass('active');
		$(this).addClass('active');
		e.preventDefault();
	});

});
