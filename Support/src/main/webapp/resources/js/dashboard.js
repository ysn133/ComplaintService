document.addEventListener('DOMContentLoaded', function() {
    const inputs = document.querySelectorAll('.input-field');
    const saveButton = document.querySelector('.save-button');

    inputs.forEach(input => {
        input.addEventListener('focus', () => {
            input.style.transform = 'scale(1.02)';
            input.previousElementSibling.style.color = '#3b82f6';
        });
        input.addEventListener('blur', () => {
            input.style.transform = 'scale(1)';
            input.previousElementSibling.style.color = '#6b7280';
        });
    });

    saveButton.addEventListener('mouseover', () => {
        saveButton.style.boxShadow = '0 0 15px rgba(96, 165, 250, 0.5)';
    });
    saveButton.addEventListener('mouseout', () => {
        saveButton.style.boxShadow = 'none';
    });

    function showToast(message, isError = true) {
        if (!message || message === 'null' || message.trim() === '') return; // Skip empty or null messages
        const toast = document.createElement('div');
        toast.className = `text-white px-4 py-2 rounded-md shadow-lg mb-2 transition-opacity duration-300 ${isError ? 'bg-red-600' : 'bg-green-600'}`;
        toast.style.opacity = '1';
        toast.textContent = message;
        document.getElementById('toast-container').appendChild(toast);
        
        setTimeout(() => {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    // Set JWT in hidden field and trigger loadProfile
    const jwtInput = document.getElementById('supportDashboard:jwt');
    const emailInput = document.getElementById('supportDashboard:email');
    const firstNameInput = document.getElementById('supportDashboard:firstName');
    const lastNameInput = document.getElementById('supportDashboard:lastName');
    if (jwtInput) {
        const jwt = localStorage.getItem('jwt') || '';
        jwtInput.value = jwt;
        if (jwt && (!emailInput.value || !firstNameInput.value || !lastNameInput.value)) {
            console.log('Triggering loadProfile due to missing field values');
            document.getElementById('supportDashboard:loadButton').click();
        } else if (!jwt) {
            showToast('Authentication token missing');
        }
    }

    // Check for error or success message
    if (typeof errorMessage !== 'undefined' && errorMessage && errorMessage !== 'null' && errorMessage.trim() !== '') {
        showToast(errorMessage);
    }
    if (typeof successMessage !== 'undefined' && successMessage && successMessage !== 'null' && successMessage.trim() !== '') {
        showToast(successMessage, false);
    }
});