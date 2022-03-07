const getModels = async (parameters) =>  {
	const baseUrl = window.location.origin;
	const url = new URL(`${window.location.pathname.split('/')[1]}/rest/getModels/${parameters.moduleCode}`, baseUrl);
	return fetch(url.toString(), {
		method: 'GET'
	});
}

const getModelsForm = (container) => {
	const html = `<form id='getModels-form'>
		<div id='getModels-moduleCode-form-field'>
			<label for='moduleCode'>moduleCode</label>
			<input type='text' id='getModels-moduleCode-param' name='moduleCode'/>
		</div>
		<button type='button'>Test</button>
	</form>`;

	container.insertAdjacentHTML('beforeend', html)

	const moduleCode = container.querySelector('#getModels-moduleCode-param');

	container.querySelector('#getModels-form button').onclick = () => {
		const params = {
			moduleCode : moduleCode.value !== "" ? moduleCode.value : undefined
		};

		getModels(params).then(r => r.text().then(
				t => alert(t)
			));
	};
}

export { getModels, getModelsForm };