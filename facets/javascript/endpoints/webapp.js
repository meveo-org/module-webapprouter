const webapp = async (parameters) =>  {
	const baseUrl = window.location.origin;
	const url = new URL(`${window.location.pathname.split('/')[1]}/rest/webapp/${parameters.appCode}`, baseUrl);
	return fetch(url.toString(), {
		method: 'GET'
	});
}

const webappForm = (container) => {
	const html = `<form id='webapp-form'>
		<div id='webapp-appCode-form-field'>
			<label for='appCode'>appCode</label>
			<input type='text' id='webapp-appCode-param' name='appCode'/>
		</div>
		<button type='button'>Test</button>
	</form>`;

	container.insertAdjacentHTML('beforeend', html)

	const appCode = container.querySelector('#webapp-appCode-param');

	container.querySelector('#webapp-form button').onclick = () => {
		const params = {
			appCode : appCode.value !== "" ? appCode.value : undefined
		};

		webapp(params).then(r => r.text().then(
				t => alert(t)
			));
	};
}

export { webapp, webappForm };